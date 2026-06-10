## [1.5.5](https://github.com/nktrapp/package-service/compare/v1.5.4...v1.5.5) (2026-06-10)


### Bug Fixes

* descarta eventos de rota stale e blinda consumo contra duplicatas e desordem ([f20271d](https://github.com/nktrapp/package-service/commit/f20271daba46a5a422fa544b3f4b580a4519abc8))

## [1.5.4](https://github.com/nktrapp/package-service/compare/v1.5.3...v1.5.4) (2026-06-09)


### Bug Fixes

* habilita OTLP tracing export por default para incluir o exporter na imagem nativa ([020bf3a](https://github.com/nktrapp/package-service/commit/020bf3a83d9f1c230c21e69487c2af8983c6a502))

## [1.5.3](https://github.com/nktrapp/package-service/compare/v1.5.2...v1.5.3) (2026-06-09)


### Bug Fixes

* compatibiliza a imagem nativa GraalVM com o ambiente local ([3068910](https://github.com/nktrapp/package-service/commit/30689106668a2de80635d2181c90386470ecbe28))
* exporta traces OTLP para o ADOT/X-Ray ([0379798](https://github.com/nktrapp/package-service/commit/03797986d3c8baed3623268a929b1e05d8b43255))

## [1.5.2](https://github.com/nktrapp/package-service/compare/v1.5.1...v1.5.2) (2026-06-09)


### Bug Fixes

* usa componentModel spring no MapStruct para compatibilidade com GraalVM native ([6672055](https://github.com/nktrapp/package-service/commit/6672055c255c68e4dd63610125756453b998bcfa))

## [1.5.1](https://github.com/nktrapp/package-service/compare/v1.5.0...v1.5.1) (2026-06-08)


### Bug Fixes

* removido profile local do spring. ([e24cfc0](https://github.com/nktrapp/package-service/commit/e24cfc0bb0c35c42f1ede1862437cd4da1b45d3e))

# [1.5.0](https://github.com/nktrapp/package-service/compare/v1.4.0...v1.5.0) (2026-06-08)


### Features

* justes de logs e observabilidade. Integrando com xray, adicionado traceID de ponta a ponta e spanId. Melhorado logs com timestamp, thread e afins. ([5072b6b](https://github.com/nktrapp/package-service/commit/5072b6b2aef5915218a2e7eb3fa7ca99c14530f5))

# [1.4.0](https://github.com/nktrapp/package-service/compare/v1.3.1...v1.4.0) (2026-06-08)


### Features

* Inicia projeto ([c35bae8](https://github.com/nktrapp/package-service/commit/c35bae8ba1accfc64acbd3ddc18fd890749dc133))
