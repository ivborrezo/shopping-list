package dev.ivborrezo.shoppinglist.list.service.list.repository;

import dev.ivborrezo.shoppinglist.list.service.list.entity.ListItem;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repositorio JPA de la entidad {@link ListItem}. */
public interface ListItemRepository extends JpaRepository<ListItem, Long> {

  /**
   * Recupera los ítems de una lista con los pendientes primero y, dentro de cada grupo, por orden
   * de inserción (id interno ascendente).
   *
   * @param listId id interno de la lista
   * @return ítems de la lista en el orden indicado
   */
  List<ListItem> findByListIdOrderByPurchasedAscIdAsc(Long listId);

  /**
   * Busca un ítem por su identificador público.
   *
   * @param publicId identificador público del ítem
   * @return el ítem si existe, o vacío si no
   */
  Optional<ListItem> findByPublicId(UUID publicId);
}
