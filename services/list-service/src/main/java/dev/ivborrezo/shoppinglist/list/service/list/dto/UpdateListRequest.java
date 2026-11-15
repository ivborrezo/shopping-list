package dev.ivborrezo.shoppinglist.list.service.list.dto;

import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/**
 * Petición de renombrado parcial de una lista de la compra.
 *
 * <p>{@code name} es opcional y, cuando llega, tiene que respetar la longitud máxima del contrato.
 * Si es {@code null}, la lista conserva su nombre actual. El propietario se deriva del token del
 * usuario autenticado y no forma parte de la petición.
 */
public record UpdateListRequest(@Size(max = 128) @Nullable String name) {}
