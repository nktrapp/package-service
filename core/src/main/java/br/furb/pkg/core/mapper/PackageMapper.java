package br.furb.pkg.core.mapper;

import br.furb.pkg.core.dto.CreatePackageCommand;
import br.furb.pkg.core.dto.PackageResponse;
import br.furb.pkg.domain.model.Package;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

@Mapper
public interface PackageMapper {

    PackageMapper INSTANCE = Mappers.getMapper(PackageMapper.class);

    PackageResponse toResponse(Package pkg);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "routeInfo", ignore = true)
    Package toDomain(CreatePackageCommand command);
}
