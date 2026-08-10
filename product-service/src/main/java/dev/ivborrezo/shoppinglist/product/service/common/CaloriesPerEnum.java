package dev.ivborrezo.shoppinglist.product.service.common;

/**
 * Unidad de referencia para el valor calórico de un producto.
 *
 * <p>Cerrado a los valores declarados en el contrato: {@code G} (por cada 100g), {@code ML} (por
 * cada 100ml), {@code UNIT} (por unidad). Nuevos valores requieren migración aditiva de esquema y
 * actualización del {@code api-contract.yaml}.
 */
public enum CaloriesPerEnum {
  G,
  ML,
  UNIT
}
