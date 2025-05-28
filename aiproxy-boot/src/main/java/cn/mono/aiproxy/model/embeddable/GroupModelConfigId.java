package cn.mono.aiproxy.model.embeddable;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Objects;

@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GroupModelConfigId implements Serializable {

    private static final long serialVersionUID = 1L;

    @Column(name = "group_id", length = 50)
    private String groupId;

    @Column(name = "model", length = 255)
    private String model;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GroupModelConfigId that = (GroupModelConfigId) o;
        return Objects.equals(groupId, that.groupId) &&
               Objects.equals(model, that.model);
    }

    @Override
    public int hashCode() {
        return Objects.hash(groupId, model);
    }
}
