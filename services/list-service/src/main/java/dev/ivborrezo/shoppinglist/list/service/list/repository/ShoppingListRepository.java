package dev.ivborrezo.shoppinglist.list.service.list.repository;

import dev.ivborrezo.shoppinglist.list.service.list.entity.ShoppingList;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repositorio JPA de la entidad {@link ShoppingList}. */
public interface ShoppingListRepository extends JpaRepository<ShoppingList, Long> {

  /**
   * Recupera las listas de un propietario, paginadas, ordenadas por actividad ({@code updated_at
   * DESC}) y, como desempate determinista, por id interno descendente.
   *
   * @param ownerId identificador del propietario
   * @param pageable parámetros de paginación
   * @return página de listas del propietario indicado
   */
  Page<ShoppingList> findByOwnerIdOrderByUpdatedAtDescIdDesc(UUID ownerId, Pageable pageable);

  /**
   * Busca una lista por su identificador público.
   *
   * @param publicId identificador público de la lista
   * @return la lista si existe, o vacío si no
   */
  Optional<ShoppingList> findByPublicId(UUID publicId);
}
