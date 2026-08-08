package dev.ivborrezo.shoppinglist.product.service.config;

import java.util.List;
import java.util.Locale;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

/** Configuración de resolución de idioma para entidades del catálogo con fallback a inglés. */
@Configuration
public class LocaleConfig {

  /**
   * Resuelve el {@link Locale} desde la cabecera {@code Accept-Language} contra la lista de idiomas
   * soportados, con fallback a {@code en} si el solicitado no está soportado o la cabecera está
   * ausente o malformada.
   *
   * @return resolver configurado con locales {@code es}, {@code en}, {@code eu} y default {@code
   *     en}
   */
  @Bean
  public LocaleResolver localeResolver() {
    AcceptHeaderLocaleResolver resolver = new AcceptHeaderLocaleResolver();
    resolver.setSupportedLocales(
        List.of(
            Locale.forLanguageTag("es"), Locale.forLanguageTag("en"), Locale.forLanguageTag("eu")));
    resolver.setDefaultLocale(Locale.forLanguageTag("en"));
    return resolver;
  }
}
