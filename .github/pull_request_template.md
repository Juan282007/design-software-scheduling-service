## Rama destino

- [ ] `develop` — trabajo en curso (caso normal)
- [ ] `qa` — validación (solo desde rama `hu-*-qa` o cherry-pick aprobado desde `develop`)
- [ ] `main` — solo desde `release/*` o `fix/*` de hotfix

## HU relacionada (obligatorio)
<!-- Reemplaza NN por el número de la HU en el repo design-software-docs.
     Sin esta línea, el env-tracking NO puede mover el ambiente de la HU en el tablero. -->
HU: code-sena/design-software-docs#NN

## Descripción
<!-- Qué cambia y por qué -->

## Tipo de cambio

- [ ] Feature nueva (HU)
- [ ] Corrección de bug / re-trabajo de review QA
- [ ] Documentación
- [ ] Infraestructura / configuración

## Definition of Done

- [ ] Cumple los criterios de aceptación de la HU
- [ ] `mvn clean verify` en verde (unit + IT contra PostgreSQL real con schema del módulo)
- [ ] JWT RS256 validado (`iss: sena-iam-service`) y autorización por feature (`SCH_*`) ejercitada en tests
- [ ] Error envelope `{error:{code,message,details,trace_id}}` conforme a `_shared.yaml`
- [ ] Auditoría con usuario real del JWT (`created_by`/`updated_by`/`deleted_by`), nunca `SYSTEM_ACTOR_ID`
- [ ] Solo changesets NUEVOS en el repo de BD (no se editan changesets ya aplicados)
- [ ] Documentación actualizada (runbook, contract, data-model)
- [ ] Aprobado por al menos un code owner
