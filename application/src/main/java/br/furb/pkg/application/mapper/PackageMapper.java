package br.furb.pkg.application.mapper;

import br.furb.pkg.application.dto.CreatePackageCommand;
import br.furb.pkg.application.dto.PackageResponse;
import br.furb.pkg.domain.model.Package;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface PackageMapper {

    PackageResponse toResponse(Package pkg);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "routeInfo", ignore = true)
    Package toDomain(CreatePackageCommand command);
}
