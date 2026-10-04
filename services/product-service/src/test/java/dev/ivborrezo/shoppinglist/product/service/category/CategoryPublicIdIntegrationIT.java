package dev.ivborrezo.shoppinglist.product.service.category;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ivborrezo.shoppinglist.product.service.category.entity.Category;
import dev.ivborrezo.shoppinglist.product.service.category.entity.CategoryTranslation;
import dev.ivborrezo.shoppinglist.product.service.category.repository.CategoryRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.jpa.test.autoconfigure.AutoConfigureTestEntityManager;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.TestConstructor.AutowireMode;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Test de integración del identificador público de {@link Category}.
 *
 * <p>Verifica que las categorías nuevas reciben un UUID v7 en {@code @PrePersist}, que el
 * identificador se mantiene inmutable al editar la entidad y que la restricción {@code UNIQUE} de
 * {@code public_id} rechaza duplicados a nivel de base de datos.
 */
@SpringBootTest(webEnvironment = WebEnvironment.MOCK)
@AutoConfigureTestEntityManager
@TestConstructor(autowireMode = AutowireMode.ALL)
@Transactional
@Testcontainers
class CategoryPublicIdIntegrationIT {

  @Container @ServiceConnection
  static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

  private final TestEntityManager entityManager;

  private final CategoryRepository categoryRepository;

  /**
   * Inyecta las dependencias de test por constructor, sin {@code @Autowired} por campo, coherente
   * con la convención del resto del monorepo.
   *
   * @param entityManager gestor JPA para inserciones ad hoc dentro de la transacción del test
   * @param categoryRepository repositorio de categorías para recargar entidades y forzar flushes
   */
  CategoryPublicIdIntegrationIT(
      TestEntityManager entityManager, CategoryRepository categoryRepository) {
    this.entityManager = entityManager;
    this.categoryRepository = categoryRepository;
  }

  /**
   * Persiste una categoría nueva con su traducción y comprueba que recibe un identificador público
   * UUID v7 y un id interno tras el flush.
   */
  @Test
  void persist_category_assignsVersion7PublicId() {
    Category category = buildCategory("public_id_feature_category");

    entityManager.persistAndFlush(category);

    assertThat(category.getPublicId()).isNotNull();
    assertThat(category.getPublicId().version()).isEqualTo(7);
    assertThat(category.getId()).isNotNull();
  }

  /**
   * Modifica el {@code code} de una categoría persistida y comprueba que el identificador público
   * permanece idéntico tras recargar la entidad desde la base de datos.
   */
  @Test
  void update_category_keepsPublicIdImmutable() {
    Category category = buildCategory("public_id_before_code");
    entityManager.persistAndFlush(category);
    final UUID publicId = category.getPublicId();

    category.setCode("public_id_after_code");
    entityManager.flush();
    entityManager.clear();

    Category reloaded = categoryRepository.findById(category.getId()).orElseThrow();

    assertThat(reloaded.getPublicId()).isEqualTo(publicId);
    assertThat(reloaded.getCode()).isEqualTo("public_id_after_code");
  }

  /**
   * Intenta persistir dos categorías con el mismo {@code publicId} fijado manualmente y comprueba
   * que la segunda viola la restricción {@code UNIQUE} de la columna {@code public_id}.
   *
   * <p>La violación deja la transacción en estado rollback-only; la excepción se captura y el test
   * no ejecuta más operaciones, dejando que {@code @Transactional} revierta el método completo.
   */
  @Test
  void duplicate_publicId_violatesUniqueConstraint() {
    UUID duplicatePublicId = UUID.randomUUID();

    Category first = buildCategory("public_id_dup_first");
    first.setPublicId(duplicatePublicId);
    categoryRepository.saveAndFlush(first);

    Category second = buildCategory("public_id_dup_second");
    second.setPublicId(duplicatePublicId);

    assertThatThrownBy(() -> categoryRepository.saveAndFlush(second))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  /**
   * Construye una categoría activa con una traducción en español y el código indicado.
   *
   * @param code código de la categoría
   * @return entidad {@link Category} sin persistir
   */
  private Category buildCategory(String code) {
    Category category = new Category();
    category.setCode(code);
    category.setIsActive(true);

    CategoryTranslation translation = new CategoryTranslation();
    translation.setLocale("es");
    translation.setName("Categoría de la feature de identificadores públicos");
    translation.setCategory(category);
    category.getTranslations().add(translation);

    return category;
  }
}
