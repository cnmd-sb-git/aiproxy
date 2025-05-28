package cn.mono.aiproxy.service;

import cn.mono.aiproxy.model.Channel;
import cn.mono.aiproxy.model.ModelConfig;
import cn.mono.aiproxy.model.dto.ChannelConfigDTO;
import cn.mono.aiproxy.repository.ChannelRepository;
import cn.mono.aiproxy.repository.ModelConfigRepository;
import cn.mono.aiproxy.service.dto.ChannelCreationRequestDTO;
import cn.mono.aiproxy.service.dto.ChannelDTO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ChannelServiceTest {

    @Mock
    private ChannelRepository channelRepository;

    @Mock
    private ModelConfigRepository modelConfigRepository;

    @Spy // Use Spy if you need to call real methods, or Mock if just verifying interactions
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private ChannelService channelService;

    private Channel channel1;
    private ChannelDTO channelDTO1;
    private ChannelCreationRequestDTO creationRequestDTO1;
    private ChannelConfigDTO configDTO;
    private String configJson;

    @BeforeEach
    void setUp() throws JsonProcessingException {
        configDTO = new ChannelConfigDTO(true);
        configJson = objectMapper.writeValueAsString(configDTO);

        channel1 = new Channel();
        channel1.setId(1);
        channel1.setName("Test Channel 1");
        channel1.setType("OPEN_AI");
        channel1.setStatus(1);
        channel1.setConfig(configJson);
        channel1.setModels(Arrays.asList("gpt-3.5-turbo", "gpt-4"));
        channel1.setCreatedAt(LocalDateTime.now());

        channelDTO1 = new ChannelDTO();
        channelDTO1.setId(1);
        channelDTO1.setName("Test Channel 1");
        channelDTO1.setType("OPEN_AI");
        channelDTO1.setStatus(1);
        channelDTO1.setConfig(configJson); // ChannelDTO stores config as JSON string
        channelDTO1.setModels(Arrays.asList("gpt-3.5-turbo", "gpt-4"));
        channelDTO1.setCreatedAt(channel1.getCreatedAt());


        creationRequestDTO1 = new ChannelCreationRequestDTO();
        creationRequestDTO1.setName("New Channel");
        creationRequestDTO1.setType("AZURE");
        creationRequestDTO1.setStatus(1);
        creationRequestDTO1.setApiKey("test-key");
        creationRequestDTO1.setModels(Arrays.asList("text-davinci-003"));
        creationRequestDTO1.setConfig(configDTO);
    }

    @Test
    void convertToDTO_shouldConvertChannelToChannelDTO() {
        ChannelDTO dto = channelService.convertToDTO(channel1);

        assertThat(dto.getId()).isEqualTo(channel1.getId());
        assertThat(dto.getName()).isEqualTo(channel1.getName());
        assertThat(dto.getConfig()).isEqualTo(configJson); // Assert JSON string
    }

    @Test
    void convertToEntity_fromChannelCreationRequestDTO_shouldConvert() throws JsonProcessingException {
        // This method is private in ChannelService, testing via createChannel/updateChannel
        // For direct test (if it were public):
        // Channel entity = channelService.convertToEntity(creationRequestDTO1, creationRequestDTO1.getApiKey());
        // assertThat(entity.getName()).isEqualTo(creationRequestDTO1.getName());
        // assertThat(entity.getConfig()).isEqualTo(objectMapper.writeValueAsString(creationRequestDTO1.getConfig()));

        // Indirect test through createChannel to ensure conversion works
        when(modelConfigRepository.existsById(anyString())).thenReturn(true);
        Channel savedChannel = new Channel(); // Simulate saved entity
        BeanUtils.copyProperties(creationRequestDTO1, savedChannel);
        savedChannel.setConfig(objectMapper.writeValueAsString(creationRequestDTO1.getConfig()));
        savedChannel.setApiKey(creationRequestDTO1.getApiKey());


        when(channelRepository.saveAll(anyList())).thenAnswer(invocation -> {
            List<Channel> channelsToSave = invocation.getArgument(0);
            // Simulate saving by setting IDs etc.
            for(int i=0; i<channelsToSave.size(); i++) {
                channelsToSave.get(i).setId(i+1);
                channelsToSave.get(i).setCreatedAt(LocalDateTime.now());
            }
            return channelsToSave;
        });


        List<ChannelDTO> resultDTOs = channelService.createChannel(creationRequestDTO1);
        assertThat(resultDTOs).hasSize(1);
        ChannelDTO resultDTO = resultDTOs.get(0);

        assertThat(resultDTO.getName()).isEqualTo(creationRequestDTO1.getName());
        assertThat(resultDTO.getConfig()).isEqualTo(objectMapper.writeValueAsString(creationRequestDTO1.getConfig()));
    }

    @Test
    void getChannelById_whenFound_shouldReturnChannelDTO() {
        when(channelRepository.findById(1)).thenReturn(Optional.of(channel1));
        Optional<ChannelDTO> result = channelService.getChannelById(1);
        assertThat(result).isPresent();
        assertThat(result.get().getName()).isEqualTo(channel1.getName());
    }

    @Test
    void getChannelById_whenNotFound_shouldReturnEmpty() {
        when(channelRepository.findById(1)).thenReturn(Optional.empty());
        Optional<ChannelDTO> result = channelService.getChannelById(1);
        assertThat(result).isNotPresent();
    }

    @Test
    void getAllChannels_shouldReturnListOfChannelDTOs() {
        when(channelRepository.findAll()).thenReturn(Collections.singletonList(channel1));
        List<ChannelDTO> results = channelService.getAllChannels();
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getName()).isEqualTo(channel1.getName());
    }

    @Test
    void createChannel_singleApiKey_shouldSaveOneChannel() {
        when(modelConfigRepository.existsById(anyString())).thenReturn(true);
        // Simulate the saved entity that would be returned by repository.save()
        Channel savedEntity = new Channel();
        BeanUtils.copyProperties(creationRequestDTO1, savedEntity, "config");
        try {
            savedEntity.setConfig(objectMapper.writeValueAsString(creationRequestDTO1.getConfig()));
        } catch (JsonProcessingException e) { throw new RuntimeException(e); }
        savedEntity.setId(1); // Simulate ID generation
        savedEntity.setCreatedAt(LocalDateTime.now());
        savedEntity.setApiKey(creationRequestDTO1.getApiKey());


        // When saveAll is called with a list containing an entity like the one we expect to be built
        when(channelRepository.saveAll(anyList())).thenAnswer(invocation -> {
             List<Channel> toSave = invocation.getArgument(0);
             if (toSave.size() == 1) {
                 Channel c = toSave.get(0);
                 c.setId(1); // Simulate save
                 c.setCreatedAt(LocalDateTime.now());
                 return Collections.singletonList(c);
             }
             return Collections.emptyList();
        });

        List<ChannelDTO> resultDTOs = channelService.createChannel(creationRequestDTO1);
        assertThat(resultDTOs).hasSize(1);
        ChannelDTO resultDTO = resultDTOs.get(0);

        assertThat(resultDTO.getName()).isEqualTo(creationRequestDTO1.getName());
        
        ArgumentCaptor<List<Channel>> listArgumentCaptor = ArgumentCaptor.forClass(List.class);
        verify(channelRepository).saveAll(listArgumentCaptor.capture());
        List<Channel> capturedList = listArgumentCaptor.getValue();
        assertThat(capturedList).hasSize(1);
        assertThat(capturedList.get(0).getApiKey()).isEqualTo(creationRequestDTO1.getApiKey());
    }

    @Test
    void createChannel_multipleApiKeys_shouldSaveMultipleChannels() {
        creationRequestDTO1.setApiKey("key1\nkey2\nkey3");
        when(modelConfigRepository.existsById(anyString())).thenReturn(true);

        // Mock saveAll to return the list passed to it, with IDs set
        when(channelRepository.saveAll(anyList())).thenAnswer(invocation -> {
            List<Channel> channels = invocation.getArgument(0);
            for (int i = 0; i < channels.size(); i++) {
                channels.get(i).setId(i + 1); // Simulate ID generation
                channels.get(i).setCreatedAt(LocalDateTime.now());
            }
            return channels;
        });

        List<ChannelDTO> resultDTOs = channelService.createChannel(creationRequestDTO1);
        assertThat(resultDTOs).hasSize(3);

        ArgumentCaptor<List<Channel>> listCaptor = ArgumentCaptor.forClass(List.class);
        verify(channelRepository).saveAll(listCaptor.capture());
        List<Channel> savedChannels = listCaptor.getValue();
        assertThat(savedChannels).hasSize(3);
        assertThat(savedChannels.get(0).getApiKey()).isEqualTo("key1");
        assertThat(savedChannels.get(1).getApiKey()).isEqualTo("key2");
        assertThat(savedChannels.get(2).getApiKey()).isEqualTo("key3");
    }

    @Test
    void createChannel_modelConfigNotFound_shouldThrowException() {
        creationRequestDTO1.setModels(Arrays.asList("unknown-model"));
        when(modelConfigRepository.existsById("unknown-model")).thenReturn(false);

        assertThatThrownBy(() -> channelService.createChannel(creationRequestDTO1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Configuration for model 'unknown-model' not found.");
    }

    @Test
    void updateChannel_whenFoundAndValid_shouldUpdateChannel() throws JsonProcessingException {
        Integer channelId = 1;
        Channel existingChannel = new Channel(); // Minimal existing channel
        existingChannel.setId(channelId);
        existingChannel.setName("Old Name");
        existingChannel.setCreatedAt(LocalDateTime.now().minusDays(1));
        existingChannel.setUsedAmount(10.0); // field to preserve
        existingChannel.setRequestCount(5); // field to preserve

        creationRequestDTO1.setName("Updated Name"); // DTO for update
        creationRequestDTO1.setModels(Arrays.asList("gpt-4"));

        when(channelRepository.findById(channelId)).thenReturn(Optional.of(existingChannel));
        when(modelConfigRepository.existsById("gpt-4")).thenReturn(true);
        
        // When save is called, the existingChannel instance will be passed (or a copy)
        // We need to ensure the mock returns the instance that would be updated.
        when(channelRepository.save(any(Channel.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Optional<ChannelDTO> resultOpt = channelService.updateChannel(channelId, creationRequestDTO1);

        assertThat(resultOpt).isPresent();
        ChannelDTO resultDTO = resultOpt.get();
        assertThat(resultDTO.getName()).isEqualTo("Updated Name");
        assertThat(resultDTO.getModels()).containsExactly("gpt-4");
        assertThat(resultDTO.getUsedAmount()).isEqualTo(10.0); // Check preserved field
        assertThat(resultDTO.getRequestCount()).isEqualTo(5);   // Check preserved field

        ArgumentCaptor<Channel> channelCaptor = ArgumentCaptor.forClass(Channel.class);
        verify(channelRepository).save(channelCaptor.capture());
        Channel savedChannel = channelCaptor.getValue();

        assertThat(savedChannel.getName()).isEqualTo("Updated Name");
        assertThat(savedChannel.getConfig()).isEqualTo(objectMapper.writeValueAsString(creationRequestDTO1.getConfig()));
        assertThat(savedChannel.getCreatedAt()).isEqualTo(existingChannel.getCreatedAt()); // Ensure original createdAt is preserved
    }

    @Test
    void updateChannel_whenNotFound_shouldReturnEmpty() {
        Integer channelId = 99;
        when(channelRepository.findById(channelId)).thenReturn(Optional.empty());

        Optional<ChannelDTO> result = channelService.updateChannel(channelId, creationRequestDTO1);
        assertThat(result).isNotPresent();
    }

    @Test
    void updateChannel_modelConfigNotFound_shouldThrowException() {
        Integer channelId = 1;
        when(channelRepository.findById(channelId)).thenReturn(Optional.of(channel1));
        creationRequestDTO1.setModels(Arrays.asList("unknown-model"));
        when(modelConfigRepository.existsById("unknown-model")).thenReturn(false);

        assertThatThrownBy(() -> channelService.updateChannel(channelId, creationRequestDTO1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Configuration for model 'unknown-model' not found.");
    }

    @Test
    void deleteChannel_whenExists_shouldCallDelete() {
        Integer channelId = 1;
        when(channelRepository.existsById(channelId)).thenReturn(true);
        doNothing().when(channelRepository).deleteById(channelId);

        boolean result = channelService.deleteChannel(channelId);
        assertThat(result).isTrue();
        verify(channelRepository).deleteById(channelId);
    }

    @Test
    void deleteChannel_whenNotExists_shouldReturnFalse() {
        Integer channelId = 1;
        when(channelRepository.existsById(channelId)).thenReturn(false);
        boolean result = channelService.deleteChannel(channelId);
        assertThat(result).isFalse();
        verify(channelRepository, never()).deleteById(channelId);
    }

    @Test
    void deleteChannelsByIds_shouldCallDeleteAll() {
        List<Integer> ids = Arrays.asList(1, 2);
        // Mock findAllById to return some channels to simulate they exist
        Channel c1 = new Channel(); c1.setId(1);
        Channel c2 = new Channel(); c2.setId(2);
        when(channelRepository.findAllById(ids)).thenReturn(Arrays.asList(c1, c2));
        doNothing().when(channelRepository).deleteAllInBatch(anyList());

        channelService.deleteChannelsByIds(ids);
        verify(channelRepository).deleteAllInBatch(anyList());
    }

    @Test
    void updateChannelStatus_whenFound_shouldUpdateStatus() {
        Integer channelId = 1;
        Integer newStatus = 0; // Disabled
        when(channelRepository.findById(channelId)).thenReturn(Optional.of(channel1));
        when(channelRepository.save(any(Channel.class))).thenReturn(channel1);

        Optional<ChannelDTO> result = channelService.updateChannelStatus(channelId, newStatus);

        assertThat(result).isPresent();
        assertThat(result.get().getStatus()).isEqualTo(newStatus);
        verify(channelRepository).save(argThat(channel -> channel.getId().equals(channelId) && channel.getStatus().equals(newStatus)));
    }

    @Test
    void searchChannels_shouldCallFindAllWithSpecification() {
        Pageable pageable = PageRequest.of(0, 10);
        List<Channel> channelList = Collections.singletonList(channel1);
        Page<Channel> channelPage = new PageImpl<>(channelList, pageable, channelList.size());

        when(channelRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(channelPage);

        Page<ChannelDTO> resultPage = channelService.searchChannels("keyword", null, null, null, null, null, pageable);

        assertThat(resultPage).isNotNull();
        assertThat(resultPage.getContent()).hasSize(1);
        assertThat(resultPage.getContent().get(0).getName()).isEqualTo(channel1.getName());
        verify(channelRepository).findAll(any(Specification.class), eq(pageable));
    }

    @Test
    void getChannelTypeMetas_shouldReturnPlaceholderData() {
        Map<String, Object> metas = channelService.getChannelTypeMetas();
        assertThat(metas).containsKey("OpenAI");
        assertThat(metas.get("OpenAI")).isInstanceOf(Map.class);
        Map<String, Object> openAIMeta = (Map<String, Object>) metas.get("OpenAI");
        assertThat(openAIMeta).containsEntry("defaultBaseUrl", "https://api.openai.com");
    }
}
