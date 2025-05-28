package cn.mono.aiproxy.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "options") // 使用 "options" 作为表名
public class OptionEntity {

    @Id
    @Column(name = "option_key", length = 255) // "key" 通常是保留关键字
    private String key;

    @Lob
    @Column(name = "option_value") // "value" 通常是保留关键字
    private String value;
}
