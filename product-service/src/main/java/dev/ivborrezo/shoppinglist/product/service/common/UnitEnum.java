package dev.ivborrezo.shoppinglist.product.service.common;

/**
 * Unidades de medida para productos del catálogo.
 *
 * <p>Cerrado a los valores declarados en el contrato de API. Nuevos valores requieren migración
 * aditiva de esquema (columna {@code default_unit}) y actualización del {@code api-contract.yaml}.
 */
public enum UnitEnum {
  KG,
  G,
  L,
  ML,
  UNIT,
  DOZEN
}
