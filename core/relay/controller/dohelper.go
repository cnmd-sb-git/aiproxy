package controller

import (
	"bytes"
	"context"
	"errors"
	"io"
	"net/http"
	"strings"
	"sync"
	"time"

	"github.com/gin-gonic/gin"
	"github.com/labring/aiproxy/core/common"
	"github.com/labring/aiproxy/core/common/conv"
	"github.com/labring/aiproxy/core/middleware"
	"github.com/labring/aiproxy/core/model"
	"github.com/labring/aiproxy/core/relay/adaptor"
	"github.com/labring/aiproxy/core/relay/adaptor/openai"
	"github.com/labring/aiproxy/core/relay/meta"
	"github.com/labring/aiproxy/core/relay/mode"
	relaymodel "github.com/labring/aiproxy/core/relay/model"
	log "github.com/sirupsen/logrus"
)

const (
	// 0.5MB
	maxBufferSize = 512 * 1024
)

type responseWriter struct {
	gin.ResponseWriter
	body        *bytes.Buffer
	firstByteAt time.Time
}

func (rw *responseWriter) Write(b []byte) (int, error) {
	if rw.firstByteAt.IsZero() {
		rw.firstByteAt = time.Now()
	}
	if total := rw.body.Len() + len(b); total <= maxBufferSize {
		rw.body.Write(b)
	} else {
		rw.body.Write(b[:maxBufferSize-rw.body.Len()])
	}
	return rw.ResponseWriter.Write(b)
}

func (rw *responseWriter) WriteString(s string) (int, error) {
	if rw.firstByteAt.IsZero() {
		rw.firstByteAt = time.Now()
	}
	if total := rw.body.Len() + len(s); total <= maxBufferSize {
		rw.body.WriteString(s)
	} else {
		rw.body.WriteString(s[:maxBufferSize-rw.body.Len()])
	}
	return rw.ResponseWriter.WriteString(s)
}

var bufferPool = sync.Pool{
	New: func() any {
		return bytes.NewBuffer(make([]byte, 0, maxBufferSize))
	},
}

func getBuffer() *bytes.Buffer {
	return bufferPool.Get().(*bytes.Buffer)
}

func putBuffer(buf *bytes.Buffer) {
	buf.Reset()
	if buf.Cap() > maxBufferSize {
		return
	}
	bufferPool.Put(buf)
}

type RequestDetail struct {
	RequestBody  string
	ResponseBody string
	FirstByteAt  time.Time
}

func DoHelper(
	a adaptor.Adaptor,
	c *gin.Context,
	meta *meta.Meta,
) (
	model.Usage,
	*RequestDetail,
	*relaymodel.ErrorWithStatusCode,
) {
	detail := RequestDetail{}

	// 1. Get request body
	if err := getRequestBody(meta, c, &detail); err != nil {
		return model.Usage{}, nil, err
	}

	// 2. Convert and prepare request
	resp, err := prepareAndDoRequest(a, c, meta)
	if err != nil {
		return model.Usage{}, &detail, err
	}

	// 3. Handle error response
	if resp == nil {
		relayErr := openai.ErrorWrapperWithMessage("response is nil", openai.ErrorCodeBadResponse, http.StatusInternalServerError)
		detail.ResponseBody = relayErr.JSONOrEmpty()
		return model.Usage{}, &detail, relayErr
	}

	defer resp.Body.Close()

	// 4. Handle success response
	usage, relayErr := handleResponse(a, c, meta, resp, &detail)
	if relayErr != nil {
		return model.Usage{}, &detail, relayErr
	}

	// 5. Update usage metrics
	updateUsageMetrics(usage, middleware.GetLogger(c))

	return usage, &detail, nil
}

func getRequestBody(meta *meta.Meta, c *gin.Context, detail *RequestDetail) *relaymodel.ErrorWithStatusCode {
	switch {
	case meta.Mode == mode.AudioTranscription,
		meta.Mode == mode.AudioTranslation,
		meta.Mode == mode.ImagesEdits:
		return nil
	case !strings.Contains(c.GetHeader("Content-Type"), "/json"):
		return nil
	default:
		reqBody, err := common.GetRequestBody(c.Request)
		if err != nil {
			return openai.ErrorWrapperWithMessage("get request body failed: "+err.Error(), "get_request_body_failed", http.StatusBadRequest)
		}
		detail.RequestBody = conv.BytesToString(reqBody)
		return nil
	}
}

func prepareAndDoRequest(a adaptor.Adaptor, c *gin.Context, meta *meta.Meta) (*http.Response, *relaymodel.ErrorWithStatusCode) {
	log := middleware.GetLogger(c)

	method, header, body, err := a.ConvertRequest(meta, c.Request)
	if err != nil {
		return nil, openai.ErrorWrapperWithMessage("convert request failed: "+err.Error(), "convert_request_failed", http.StatusBadRequest)
	}
	if closer, ok := body.(io.Closer); ok {
		defer closer.Close()
	}

	if meta.Channel.BaseURL == "" {
		meta.Channel.BaseURL = a.GetBaseURL()
	}

	fullRequestURL, err := a.GetRequestURL(meta)
	if err != nil {
		return nil, openai.ErrorWrapperWithMessage("get request url failed: "+err.Error(), "get_request_url_failed", http.StatusBadRequest)
	}

	log.Debugf("request url: %s %s", method, fullRequestURL)

	ctx := context.Background()
	if timeout := meta.ModelConfig.Timeout; timeout > 0 {
		// donot use c.Request.Context() because it will be canceled by the client
		// which will cause the usage of non-streaming requests to be unable to be recorded
		var cancel context.CancelFunc
		ctx, cancel = context.WithTimeout(ctx, time.Duration(timeout)*time.Second)
		defer cancel()
	}

	req, err := http.NewRequestWithContext(ctx, method, fullRequestURL, body)
	if err != nil {
		return nil, openai.ErrorWrapperWithMessage("new request failed: "+err.Error(), "new_request_failed", http.StatusBadRequest)
	}

	if err := setupRequestHeader(a, c, meta, req, header); err != nil {
		return nil, err
	}

	return doRequest(a, c, meta, req)
}

func setupRequestHeader(a adaptor.Adaptor, c *gin.Context, meta *meta.Meta, req *http.Request, header http.Header) *relaymodel.ErrorWithStatusCode {
	contentType := req.Header.Get("Content-Type")
	if contentType == "" {
		contentType = "application/json; charset=utf-8"
	}
	req.Header.Set("Content-Type", contentType)
	for key, value := range header {
		req.Header[key] = value
	}
	if err := a.SetupRequestHeader(meta, c, req); err != nil {
		return openai.ErrorWrapperWithMessage("setup request header failed: "+err.Error(), "setup_request_header_failed", http.StatusInternalServerError)
	}
	return nil
}

func doRequest(a adaptor.Adaptor, c *gin.Context, meta *meta.Meta, req *http.Request) (*http.Response, *relaymodel.ErrorWithStatusCode) {
	resp, err := a.DoRequest(meta, c, req)
	if err != nil {
		if errors.Is(err, context.Canceled) {
			return nil, openai.ErrorWrapperWithMessage("do request failed: request canceled by client", "request_canceled", http.StatusBadRequest)
		}
		if errors.Is(err, context.DeadlineExceeded) {
			return nil, openai.ErrorWrapperWithMessage("do request failed: request timeout", "request_timeout", http.StatusGatewayTimeout)
		}
		if errors.Is(err, io.EOF) {
			return nil, openai.ErrorWrapperWithMessage("do request failed: "+err.Error(), "request_failed", http.StatusServiceUnavailable)
		}
		if errors.Is(err, io.ErrUnexpectedEOF) {
			return nil, openai.ErrorWrapperWithMessage("do request failed: "+err.Error(), "request_failed", http.StatusInternalServerError)
		}
		return nil, openai.ErrorWrapperWithMessage("do request failed: "+err.Error(), "request_failed", http.StatusBadRequest)
	}
	return resp, nil
}

func handleResponse(a adaptor.Adaptor, c *gin.Context, meta *meta.Meta, resp *http.Response, detail *RequestDetail) (model.Usage, *relaymodel.ErrorWithStatusCode) {
	buf := getBuffer()
	defer putBuffer(buf)

	rw := &responseWriter{
		ResponseWriter: c.Writer,
		body:           buf,
	}
	rawWriter := c.Writer
	defer func() {
		c.Writer = rawWriter
		detail.FirstByteAt = rw.firstByteAt
	}()
	c.Writer = rw

	c.Header("Content-Type", resp.Header.Get("Content-Type"))

	usage, relayErr := a.DoResponse(meta, c, resp)
	if relayErr != nil {
		detail.ResponseBody = relayErr.JSONOrEmpty()
	} else {
		// copy body buffer
		// do not use bytes conv
		detail.ResponseBody = rw.body.String()
	}

	if usage != nil {
		return *usage, relayErr
	}

	if relayErr != nil {
		return model.Usage{}, relayErr
	}

	return meta.RequestUsage, nil
}

func updateUsageMetrics(usage model.Usage, log *log.Entry) {
	if usage.TotalTokens == 0 {
		usage.TotalTokens = usage.InputTokens + usage.OutputTokens
	}
	if usage.InputTokens > 0 {
		log.Data["t_input"] = usage.InputTokens
	}
	if usage.ImageInputTokens > 0 {
		log.Data["t_image_input"] = usage.ImageInputTokens
	}
	if usage.OutputTokens > 0 {
		log.Data["t_output"] = usage.OutputTokens
	}
	log.Data["t_total"] = usage.TotalTokens
	if usage.CachedTokens > 0 {
		log.Data["t_cached"] = usage.CachedTokens
	}
	if usage.CacheCreationTokens > 0 {
		log.Data["t_cache_creation"] = usage.CacheCreationTokens
	}
	if usage.ReasoningTokens > 0 {
		log.Data["t_reason"] = usage.ReasoningTokens
	}
	if usage.WebSearchCount > 0 {
		log.Data["t_websearch"] = usage.WebSearchCount
	}
}
