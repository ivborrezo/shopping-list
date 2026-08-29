package dev.ivborrezo.shoppinglist.product.service.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Habilita JPA Auditing en el servicio para que las anotaciones {@link
 * org.springframework.data.annotation.CreatedDate @CreatedDate} y {@link
 * org.springframework.data.annotation.LastModifiedDate @LastModifiedDate} escriban automáticamente
 * las columnas de auditoría de las entidades.
 */
@Configuration
@EnableJpaAuditing
public class AuditingConfig {}
