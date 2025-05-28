package cn.mono.aiproxy.service;

import cn.mono.aiproxy.config.AppConfig;
import cn.mono.aiproxy.model.OptionEntity;
import cn.mono.aiproxy.repository.OptionRepository;
import cn.mono.aiproxy.service.dto.OptionDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class OptionServiceTest {

    @Mock
    private OptionRepository optionRepository;

    @Spy // Using Spy for AppConfig to allow real method calls for defaults, but still track sets
    private AppConfig appConfig = new AppConfig(); 

    @Spy // ObjectMapper can be spied or a real instance can be used if no specific mock behavior needed for it here
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private OptionService optionService;

    private OptionEntity optionEntity1;
    private OptionDTO optionDTO1;

    @BeforeEach
    void setUp() {
        optionEntity1 = new OptionEntity("LogStorageHours", "720");
        optionDTO1 = new OptionDTO("LogStorageHours", "720");
        
        // Reset AppConfig to defaults before each test that might modify it
        appConfig = new AppConfig(); 
        // Re-inject the spied AppConfig if necessary or ensure OptionService uses this instance.
        // InjectMocks handles this, but if AppConfig is modified directly by tests, ensure it's the one OptionService sees.
        // For OptionService, the @InjectMocks should correctly inject the spied appConfig.
        // The OptionService constructor explicitly takes AppConfig, so it's fine.
    }

    @Test
    void getAllOptions_shouldReturnAllOptions() {
        when(optionRepository.findAll()).thenReturn(Arrays.asList(optionEntity1, new OptionEntity("RetryTimes", "3")));

        List<OptionDTO> result = optionService.getAllOptions();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getKey()).isEqualTo("LogStorageHours");
        assertThat(result.get(1).getKey()).isEqualTo("RetryTimes");
        verify(optionRepository).findAll();
    }

    @Test
    void getOptionByKey_whenFound_shouldReturnOption() {
        when(optionRepository.findById("LogStorageHours")).thenReturn(Optional.of(optionEntity1));

        Optional<OptionDTO> result = optionService.getOptionByKey("LogStorageHours");

        assertThat(result).isPresent();
        assertThat(result.get().getKey()).isEqualTo("LogStorageHours");
        assertThat(result.get().getValue()).isEqualTo("720");
        verify(optionRepository).findById("LogStorageHours");
    }

    @Test
    void getOptionByKey_whenNotFound_shouldReturnEmpty() {
        when(optionRepository.findById("NonExistentKey")).thenReturn(Optional.empty());

        Optional<OptionDTO> result = optionService.getOptionByKey("NonExistentKey");

        assertThat(result).isNotPresent();
        verify(optionRepository).findById("NonExistentKey");
    }

    @Test
    void updateOption_shouldSaveAndRefreshAppConfig() {
        OptionDTO toUpdate = new OptionDTO("LogStorageHours", "360");
        OptionEntity updatedEntity = new OptionEntity("LogStorageHours", "360");

        when(optionRepository.save(any(OptionEntity.class))).thenReturn(updatedEntity);
        // appConfig is a spy, so its setters will be called for real if not mocked

        OptionDTO result = optionService.updateOption(toUpdate);

        assertThat(result.getValue()).isEqualTo("360");
        verify(optionRepository).save(argThat(entity -> entity.getKey().equals("LogStorageHours") && entity.getValue().equals("360")));
        
        // Verify AppConfig was updated
        assertThat(appConfig.getLogStorageHours()).isEqualTo(360L); 
    }
    
    @Test
    void updateOption_withJsonValue_shouldSaveAndRefreshAppConfig() {
        String jsonValue = "{\"1\": 30}";
        OptionDTO toUpdate = new OptionDTO("TimeoutWithModelType", jsonValue);
        OptionEntity updatedEntity = new OptionEntity("TimeoutWithModelType", jsonValue);

        when(optionRepository.save(any(OptionEntity.class))).thenReturn(updatedEntity);

        OptionDTO result = optionService.updateOption(toUpdate);

        assertThat(result.getValue()).isEqualTo(jsonValue);
        verify(optionRepository).save(argThat(entity -> entity.getKey().equals("TimeoutWithModelType") && entity.getValue().equals(jsonValue)));
        assertThat(appConfig.getTimeoutWithModelType()).isEqualTo(jsonValue);
        assertThat(appConfig.getTimeoutWithModelTypeMap()).containsEntry(1, 30L);
    }


    @Test
    void updateOptions_shouldSaveMultipleAndRefreshAppConfig() {
        Map<String, String> optionsToUpdate = Map.of(
                "LogStorageHours", "100",
                "RetryTimes", "5"
        );

        OptionEntity logEntity = new OptionEntity("LogStorageHours", "100");
        OptionEntity retryEntity = new OptionEntity("RetryTimes", "5");

        // Mock findById for each key to simulate existing entities (or not, save handles upsert)
        when(optionRepository.findById("LogStorageHours")).thenReturn(Optional.of(new OptionEntity("LogStorageHours", "720")));
        when(optionRepository.findById("RetryTimes")).thenReturn(Optional.of(new OptionEntity("RetryTimes", "3")));
        
        when(optionRepository.save(argThat(e -> e.getKey().equals("LogStorageHours")))).thenReturn(logEntity);
        when(optionRepository.save(argThat(e -> e.getKey().equals("RetryTimes")))).thenReturn(retryEntity);

        List<OptionDTO> results = optionService.updateOptions(optionsToUpdate);

        assertThat(results).hasSize(2);
        verify(optionRepository, times(2)).save(any(OptionEntity.class));
        
        // Verify AppConfig updates
        assertThat(appConfig.getLogStorageHours()).isEqualTo(100L);
        assertThat(appConfig.getRetryTimes()).isEqualTo(5L);
    }

    @Test
    void loadOptionsOnStartup_whenDBHasAllOptions_shouldLoadToAppConfig() {
        List<OptionEntity> dbOptions = Arrays.asList(
                new OptionEntity("LogStorageHours", "500"),
                new OptionEntity("RetryTimes", "10"),
                new OptionEntity("TimeoutWithModelType", "{\"1\": 10}")
        );
        when(optionRepository.findAll()).thenReturn(dbOptions);

        optionService.loadOptionsOnStartup();

        // Verify AppConfig was updated with DB values
        assertThat(appConfig.getLogStorageHours()).isEqualTo(500L);
        assertThat(appConfig.getRetryTimes()).isEqualTo(10L);
        assertThat(appConfig.getTimeoutWithModelTypeMap()).containsEntry(1, 10L);
        
        verify(optionRepository, never()).save(any(OptionEntity.class)); // No new options saved
    }

    @Test
    void loadOptionsOnStartup_whenDBIsEmpty_shouldPersistDefaultsAndLoadToAppConfig() {
        when(optionRepository.findAll()).thenReturn(Collections.emptyList());
        when(optionRepository.save(any(OptionEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        
        // AppConfig spy already has its constructor-set defaults
        // e.g., LogStorageHours = 720L, RetryTimes = 3L

        optionService.loadOptionsOnStartup();

        // Verify AppConfig retains its defaults (as DB was empty, then defaults were "loaded" back)
        assertThat(appConfig.getLogStorageHours()).isEqualTo(720L);
        assertThat(appConfig.getRetryTimes()).isEqualTo(3L);
        assertThat(appConfig.getTimeoutWithModelType()).isEqualTo("{}"); // Default from AppConfig

        // Verify that all default options were saved to DB
        ArgumentCaptor<OptionEntity> optionCaptor = ArgumentCaptor.forClass(OptionEntity.class);
        // Number of defaults defined in OptionService.OPTION_DEFAULTS
        int expectedSaves = OptionService.OPTION_DEFAULTS.size(); 
        verify(optionRepository, times(expectedSaves)).save(optionCaptor.capture());

        // Check one of the saved entities
        Optional<OptionEntity> savedLogStorage = optionCaptor.getAllValues().stream()
                .filter(opt -> opt.getKey().equals("LogStorageHours"))
                .findFirst();
        assertThat(savedLogStorage).isPresent();
        assertThat(savedLogStorage.get().getValue()).isEqualTo("720"); // Default value
    }
    
    @Test
    void loadOptionsOnStartup_whenDBIsMissingSomeOptions_shouldLoadExistingAndPersistDefaultsForMissing() {
        // DB only has RetryTimes
        List<OptionEntity> dbOptions = Collections.singletonList(new OptionEntity("RetryTimes", "7"));
        when(optionRepository.findAll()).thenReturn(dbOptions);
        when(optionRepository.save(any(OptionEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // AppConfig spy has constructor defaults (LogStorageHours=720L, RetryTimes=3L initially)
        
        optionService.loadOptionsOnStartup();

        // Verify AppConfig: RetryTimes from DB, LogStorageHours is default
        assertThat(appConfig.getRetryTimes()).isEqualTo(7L); // From DB
        assertThat(appConfig.getLogStorageHours()).isEqualTo(720L); // Default, was missing in DB

        // Verify save was called for missing options
        int expectedMissingSaves = OptionService.OPTION_DEFAULTS.size() - 1;
        verify(optionRepository, times(expectedMissingSaves)).save(any(OptionEntity.class));
        
        ArgumentCaptor<OptionEntity> savedCaptor = ArgumentCaptor.forClass(OptionEntity.class);
        verify(optionRepository, times(expectedMissingSaves)).save(savedCaptor.capture());
        
        // Ensure RetryTimes was NOT saved (it came from DB)
        assertThat(savedCaptor.getAllValues().stream().noneMatch(e -> e.getKey().equals("RetryTimes"))).isTrue();
        // Ensure LogStorageHours WAS saved (it was missing)
        assertThat(savedCaptor.getAllValues().stream().anyMatch(e -> e.getKey().equals("LogStorageHours") && e.getValue().equals("720"))).isTrue();
    }
    
    @Test
    void updateOption_whenValueIsNull_shouldUseDefaultFromAppConfig() {
        OptionDTO toUpdateWithNull = new OptionDTO("LogStorageHours", null);
        // AppConfig.getLogStorageHours() default is 720L
        OptionEntity expectedSaveEntity = new OptionEntity("LogStorageHours", "720"); 

        when(optionRepository.save(any(OptionEntity.class))).thenReturn(expectedSaveEntity);

        optionService.updateOption(toUpdateWithNull);

        verify(optionRepository).save(argThat(entity -> 
            entity.getKey().equals("LogStorageHours") && entity.getValue().equals("720")
        ));
        assertThat(appConfig.getLogStorageHours()).isEqualTo(720L);
    }
    
    @Test
    void updateOption_forJsonField_whenValueIsNull_shouldUseDefaultJsonString() {
        OptionDTO toUpdateWithNull = new OptionDTO("TimeoutWithModelType", null);
        // AppConfig.getTimeoutWithModelType() default is "{}"
        OptionEntity expectedSaveEntity = new OptionEntity("TimeoutWithModelType", "{}");

        when(optionRepository.save(any(OptionEntity.class))).thenReturn(expectedSaveEntity);

        optionService.updateOption(toUpdateWithNull);

        verify(optionRepository).save(argThat(entity ->
            entity.getKey().equals("TimeoutWithModelType") && entity.getValue().equals("{}")
        ));
        assertThat(appConfig.getTimeoutWithModelType()).isEqualTo("{}");
    }
    
    @Test
    void updateOption_withInvalidNumericValue_shouldLogErrorAndUseDefault() {
        OptionDTO toUpdate = new OptionDTO("LogStorageHours", "not-a-number");
        // AppConfig.getLogStorageHours() default is 720L
        OptionEntity savedEntityIfValid = new OptionEntity("LogStorageHours", "not-a-number"); // This would be saved
        OptionEntity defaultEntity = new OptionEntity("LogStorageHours", "720");


        when(optionRepository.save(any(OptionEntity.class))).thenReturn(savedEntityIfValid); // Save is called first
        // Then updateAppConfig is called, which will find "not-a-number" and try to parse
        // It will fail, log error, and then internally it calls updateAppConfig again with default.

        optionService.updateOption(toUpdate);

        // save is called with "not-a-number"
        verify(optionRepository).save(argThat(e -> e.getValue().equals("not-a-number")));
        
        // AppConfig should end up with the default value due to parsing error and fallback
        assertThat(appConfig.getLogStorageHours()).isEqualTo(720L);
    }

    @Test
    void updateOption_withInvalidJsonValue_shouldLogErrorAndUseDefault() {
        OptionDTO toUpdate = new OptionDTO("TimeoutWithModelType", "invalid-json");
        OptionEntity savedEntityIfValid = new OptionEntity("TimeoutWithModelType", "invalid-json");
        // AppConfig default for TimeoutWithModelType is "{}"

        when(optionRepository.save(any(OptionEntity.class))).thenReturn(savedEntityIfValid);

        optionService.updateOption(toUpdate);
        
        verify(optionRepository).save(argThat(e -> e.getValue().equals("invalid-json")));
        assertThat(appConfig.getTimeoutWithModelType()).isEqualTo("{}"); // Default due to validation error
    }
}
