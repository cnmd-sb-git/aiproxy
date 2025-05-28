package cn.mono.aiproxy.service;

import cn.mono.aiproxy.model.GroupEntity;
import cn.mono.aiproxy.model.GroupModelConfigEntity;
import cn.mono.aiproxy.model.Log;
import cn.mono.aiproxy.model.embeddable.GroupModelConfigId;
import cn.mono.aiproxy.model.embeddable.PriceEmbeddable;
import cn.mono.aiproxy.repository.GroupModelConfigRepository;
import cn.mono.aiproxy.repository.GroupRepository;
import cn.mono.aiproxy.repository.LogRepository;
import cn.mono.aiproxy.service.dto.GroupCreationRequestDTO;
import cn.mono.aiproxy.service.dto.GroupDTO;
import cn.mono.aiproxy.service.dto.GroupModelConfigCreationDTO;
import cn.mono.aiproxy.service.dto.GroupModelConfigDTO;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class GroupServiceTest {

    @Mock
    private GroupRepository groupRepository;

    @Mock
    private GroupModelConfigRepository groupModelConfigRepository;

    @Mock
    private LogRepository logRepository;

    @Spy // If ObjectMapper is used for complex JSON logic in service, otherwise @Mock or real
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private GroupService groupService;

    private GroupEntity groupEntity1;
    private GroupCreationRequestDTO groupCreationRequestDTO1;
    private GroupModelConfigEntity groupModelConfigEntity1;
    private GroupModelConfigCreationDTO groupModelConfigCreationDTO1;
    private Log sampleLog;

    @BeforeEach
    void setUp() {
        groupEntity1 = new GroupEntity();
        groupEntity1.setId("test-group-1");
        groupEntity1.setStatus(1);
        groupEntity1.setRpmRatio(1.0);
        groupEntity1.setTpmRatio(1.0);
        groupEntity1.setCreatedAt(LocalDateTime.now().minusDays(1));

        groupCreationRequestDTO1 = new GroupCreationRequestDTO();
        groupCreationRequestDTO1.setId("test-group-1");
        groupCreationRequestDTO1.setStatus(1);
        groupCreationRequestDTO1.setRpmRatio(1.0);
        groupCreationRequestDTO1.setTpmRatio(1.0);
        groupCreationRequestDTO1.setAvailableSets(Arrays.asList("set1", "set2"));

        groupModelConfigEntity1 = new GroupModelConfigEntity();
        groupModelConfigEntity1.setId(new GroupModelConfigId("test-group-1", "gpt-4"));
        groupModelConfigEntity1.setGroup(groupEntity1);
        groupModelConfigEntity1.setRpm(100L);
        groupModelConfigEntity1.setPrice(new PriceEmbeddable());

        groupModelConfigCreationDTO1 = new GroupModelConfigCreationDTO();
        groupModelConfigCreationDTO1.setModel("gpt-4");
        groupModelConfigCreationDTO1.setRpm(100L);
        groupModelConfigCreationDTO1.setPrice(new PriceEmbeddable());
        
        sampleLog = new Log();
        sampleLog.setId(1);
        sampleLog.setGroupId("test-group-1");
        sampleLog.setRequestAt(LocalDateTime.now().minusHours(1));
    }

    @Test
    void convertToDTO_GroupEntity_shouldIncludeAccessedAt() {
        when(logRepository.findTopByGroupIdOrderByRequestAtDesc("test-group-1")).thenReturn(Optional.of(sampleLog));
        GroupDTO dto = groupService.convertToDTO(groupEntity1);
        assertThat(dto.getId()).isEqualTo(groupEntity1.getId());
        assertThat(dto.getAccessedAt()).isEqualTo(sampleLog.getRequestAt());
    }

    @Test
    void convertToDTO_GroupEntity_noLogs_accessedAtShouldBeNull() {
        when(logRepository.findTopByGroupIdOrderByRequestAtDesc("test-group-1")).thenReturn(Optional.empty());
        GroupDTO dto = groupService.convertToDTO(groupEntity1);
        assertThat(dto.getAccessedAt()).isNull();
    }
    
    @Test
    void convertToEntity_GroupCreationRequestDTO_shouldConvert() {
        GroupEntity entity = groupService.convertToEntity(groupCreationRequestDTO1);
        assertThat(entity.getId()).isEqualTo(groupCreationRequestDTO1.getId());
        assertThat(entity.getRpmRatio()).isEqualTo(groupCreationRequestDTO1.getRpmRatio());
        assertThat(entity.getAvailableSets()).isEqualTo(groupCreationRequestDTO1.getAvailableSets());
        assertThat(entity.getStatus()).isEqualTo(1); // Default status
    }

    @Test
    void convertToDTO_GroupModelConfigEntity_shouldConvert() {
        GroupModelConfigDTO dto = groupService.convertToDTO(groupModelConfigEntity1);
        assertThat(dto.getGroupId()).isEqualTo(groupModelConfigEntity1.getId().getGroupId());
        assertThat(dto.getModel()).isEqualTo(groupModelConfigEntity1.getId().getModel());
        assertThat(dto.getRpm()).isEqualTo(groupModelConfigEntity1.getRpm());
    }
    
    @Test
    void convertToEntity_GroupModelConfigCreationDTO_shouldConvert() {
        GroupModelConfigEntity entity = groupService.convertToEntity("test-group-1", groupModelConfigCreationDTO1);
        assertThat(entity.getId().getGroupId()).isEqualTo("test-group-1");
        assertThat(entity.getId().getModel()).isEqualTo(groupModelConfigCreationDTO1.getModel());
        assertThat(entity.getRpm()).isEqualTo(groupModelConfigCreationDTO1.getRpm());
    }


    @Test
    void createGroup_shouldSaveAndReturnDTO() {
        when(groupRepository.existsById("test-group-1")).thenReturn(false);
        when(groupRepository.save(any(GroupEntity.class))).thenReturn(groupEntity1);
        when(logRepository.findTopByGroupIdOrderByRequestAtDesc(anyString())).thenReturn(Optional.empty());


        GroupDTO result = groupService.createGroup(groupCreationRequestDTO1);

        assertThat(result.getId()).isEqualTo(groupCreationRequestDTO1.getId());
        verify(groupRepository).save(any(GroupEntity.class));
    }
    
    @Test
    void createGroup_withEmptyId_shouldThrowException() {
        groupCreationRequestDTO1.setId("");
        assertThatThrownBy(() -> groupService.createGroup(groupCreationRequestDTO1))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Group ID must not be empty.");
    }

    @Test
    void createGroup_whenGroupExists_shouldThrowException() {
        when(groupRepository.existsById("test-group-1")).thenReturn(true);
        assertThatThrownBy(() -> groupService.createGroup(groupCreationRequestDTO1))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Group with ID 'test-group-1' already exists.");
    }


    @Test
    void getGroupById_whenFound_shouldReturnGroupDTOWithAccessedAt() {
        when(groupRepository.findById("test-group-1")).thenReturn(Optional.of(groupEntity1));
        when(logRepository.findTopByGroupIdOrderByRequestAtDesc("test-group-1")).thenReturn(Optional.of(sampleLog));

        Optional<GroupDTO> result = groupService.getGroupById("test-group-1");

        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(groupEntity1.getId());
        assertThat(result.get().getAccessedAt()).isEqualTo(sampleLog.getRequestAt());
    }

    @Test
    void getGroupById_whenNotFound_shouldReturnEmpty() {
        when(groupRepository.findById("unknown-group")).thenReturn(Optional.empty());
        Optional<GroupDTO> result = groupService.getGroupById("unknown-group");
        assertThat(result).isNotPresent();
    }

    @Test
    void getAllGroups_shouldReturnPagedGroupDTOs() {
        Pageable pageable = PageRequest.of(0, 1);
        List<GroupEntity> groupList = Collections.singletonList(groupEntity1);
        Page<GroupEntity> groupPage = new PageImpl<>(groupList, pageable, groupList.size());

        when(groupRepository.findAll(pageable)).thenReturn(groupPage);
        when(logRepository.findTopByGroupIdOrderByRequestAtDesc(groupEntity1.getId())).thenReturn(Optional.of(sampleLog));

        Page<GroupDTO> resultPage = groupService.getAllGroups(pageable);

        assertThat(resultPage.getTotalElements()).isEqualTo(1);
        assertThat(resultPage.getContent().get(0).getId()).isEqualTo(groupEntity1.getId());
        assertThat(resultPage.getContent().get(0).getAccessedAt()).isEqualTo(sampleLog.getRequestAt());
    }
    
    @Test
    void searchGroups_shouldReturnFilteredAndPagedResults() {
        Pageable pageable = PageRequest.of(0, 1);
        List<GroupEntity> groupList = Collections.singletonList(groupEntity1);
        Page<GroupEntity> groupPage = new PageImpl<>(groupList, pageable, groupList.size());

        when(groupRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(groupPage);
        when(logRepository.findTopByGroupIdOrderByRequestAtDesc(groupEntity1.getId())).thenReturn(Optional.of(sampleLog));
        
        Page<GroupDTO> resultPage = groupService.searchGroups("test", 1, pageable);
        
        assertThat(resultPage.getTotalElements()).isEqualTo(1);
        assertThat(resultPage.getContent().get(0).getId()).isEqualTo(groupEntity1.getId());
    }


    @Test
    void updateGroup_whenFound_shouldUpdateAndReturnDTO() {
        GroupCreationRequestDTO updateDto = new GroupCreationRequestDTO();
        updateDto.setRpmRatio(2.0);
        updateDto.setStatus(0);

        when(groupRepository.findById("test-group-1")).thenReturn(Optional.of(groupEntity1));
        when(groupRepository.save(any(GroupEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(logRepository.findTopByGroupIdOrderByRequestAtDesc(anyString())).thenReturn(Optional.empty());


        Optional<GroupDTO> result = groupService.updateGroup("test-group-1", updateDto);

        assertThat(result).isPresent();
        assertThat(result.get().getRpmRatio()).isEqualTo(2.0);
        assertThat(result.get().getStatus()).isEqualTo(0);
        verify(groupRepository).save(any(GroupEntity.class));
    }
    
    @Test
    void updateGroupStatus_shouldUpdateStatus() {
        when(groupRepository.findById("test-group-1")).thenReturn(Optional.of(groupEntity1));
        when(groupRepository.save(any(GroupEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(logRepository.findTopByGroupIdOrderByRequestAtDesc(anyString())).thenReturn(Optional.empty());

        Optional<GroupDTO> result = groupService.updateGroupStatus("test-group-1", 0);
        assertThat(result).isPresent();
        assertThat(result.get().getStatus()).isEqualTo(0);
    }


    @Test
    void deleteGroup_whenExists_shouldCallDelete() {
        when(groupRepository.existsById("test-group-1")).thenReturn(true);
        doNothing().when(groupRepository).deleteById("test-group-1");

        groupService.deleteGroup("test-group-1");
        verify(groupRepository).deleteById("test-group-1");
    }

    @Test
    void deleteGroup_whenNotExists_shouldThrowEntityNotFound() {
        when(groupRepository.existsById("unknown-group")).thenReturn(false);
        assertThatThrownBy(() -> groupService.deleteGroup("unknown-group"))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void getGroupModelConfigs_whenGroupNotFound_shouldThrowEntityNotFound() {
        when(groupRepository.existsById("unknown-group")).thenReturn(false);
        assertThatThrownBy(() -> groupService.getGroupModelConfigs("unknown-group"))
            .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void getGroupModelConfigs_shouldReturnListOfDTOs() {
        when(groupRepository.existsById("test-group-1")).thenReturn(true);
        when(groupModelConfigRepository.findByGroupId("test-group-1")).thenReturn(Collections.singletonList(groupModelConfigEntity1));
        List<GroupModelConfigDTO> result = groupService.getGroupModelConfigs("test-group-1");
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getModel()).isEqualTo("gpt-4");
    }
    
    @Test
    void getGroupModelConfig_whenFound_shouldReturnDTO() {
        GroupModelConfigId id = new GroupModelConfigId("test-group-1", "gpt-4");
        when(groupModelConfigRepository.findById(id)).thenReturn(Optional.of(groupModelConfigEntity1));
        Optional<GroupModelConfigDTO> result = groupService.getGroupModelConfig("test-group-1", "gpt-4");
        assertThat(result).isPresent();
        assertThat(result.get().getModel()).isEqualTo("gpt-4");
    }

    @Test
    void saveGroupModelConfigs_shouldSaveAllAndReturnDTOs() {
        when(groupRepository.findById("test-group-1")).thenReturn(Optional.of(groupEntity1));
        when(groupModelConfigRepository.findById(any(GroupModelConfigId.class))).thenReturn(Optional.empty()); // Simulate new configs
        when(groupModelConfigRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        List<GroupModelConfigCreationDTO> dtoList = Collections.singletonList(groupModelConfigCreationDTO1);
        List<GroupModelConfigDTO> result = groupService.saveGroupModelConfigs("test-group-1", dtoList);

        assertThat(result).hasSize(1);
        verify(groupModelConfigRepository).saveAll(anyList());
        ArgumentCaptor<List<GroupModelConfigEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(groupModelConfigRepository).saveAll(captor.capture());
        assertThat(captor.getValue().get(0).getGroup()).isEqualTo(groupEntity1); // Ensure group is set
    }

    @Test
    void deleteGroupModelConfig_whenExists_shouldCallDelete() {
        GroupModelConfigId id = new GroupModelConfigId("test-group-1", "gpt-4");
        when(groupModelConfigRepository.existsById(id)).thenReturn(true);
        doNothing().when(groupModelConfigRepository).deleteById(id);

        groupService.deleteGroupModelConfig("test-group-1", "gpt-4");
        verify(groupModelConfigRepository).deleteById(id);
    }
    
    @Test
    void deleteGroupModelConfig_whenNotExists_shouldThrowEntityNotFound() {
        GroupModelConfigId id = new GroupModelConfigId("test-group-1", "gpt-4");
        when(groupModelConfigRepository.existsById(id)).thenReturn(false);
        assertThatThrownBy(() -> groupService.deleteGroupModelConfig("test-group-1", "gpt-4"))
            .isInstanceOf(EntityNotFoundException.class);
    }


    @Test
    void getIpGroupList_shouldThrowUnsupportedOperationException() {
        assertThatThrownBy(() -> groupService.getIpGroupList())
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("getIpGroupList is not yet implemented.");
    }
}
