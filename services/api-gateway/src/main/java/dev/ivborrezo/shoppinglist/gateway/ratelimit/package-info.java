/**
 * Rate limiting del borde por IP de socket.
 *
 * <p>El límite se aplica en memoria y por instancia con un token bucket: cada clave dispone de una
 * capacidad de ráfaga y una recarga continua, y los cubos inactivos se purgan periódicamente.
 */
@NullMarked
package dev.ivborrezo.shoppinglist.gateway.ratelimit;

import org.jspecify.annotations.NullMarked;
