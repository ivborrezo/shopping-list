package dev.ivborrezo.shoppinglist.product.service.common.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Inyecta en un parámetro {@code UUID} de un método de controller el identificador del usuario
 * autenticado.
 *
 * <p>El valor se extrae del JWT validado en el contexto de seguridad, de modo que la capa web no
 * dependa de las clases de infraestructura OAuth2.
 */
@Documented
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface CurrentUserId {}
