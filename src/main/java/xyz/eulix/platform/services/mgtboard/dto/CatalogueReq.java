package xyz.eulix.platform.services.mgtboard.dto;

import lombok.Data;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

@Data(staticConstructor = "of")
public class CatalogueReq {
    @NotBlank
    @Size(max = 10)
    @Schema(required = true, description = "目录名字")
    private String cataName;

    @Schema(required = true, description = "父目录id")
    private Long parentId;
}
