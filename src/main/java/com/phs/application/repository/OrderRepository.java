package com.phs.application.repository;

import com.phs.application.entity.Order;
import com.phs.application.model.dto.OrderDetailDTO;
import com.phs.application.model.dto.OrderInfoDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
    @Query(value = "SELECT * FROM orders " +
            "WHERE id LIKE CONCAT('%',?1,'%') " +
            "AND receiver_name LIKE CONCAT('%',?2,'%') " +
            "AND receiver_phone LIKE CONCAT('%',?3,'%') " +
            "AND status LIKE CONCAT('%',?4,'%') " +
            "AND product_id LIKE CONCAT('%',?5,'%')" +
            "ORDER BY id DESC", nativeQuery = true)
    Page<Order> adminGetListOrder(String id, String name, String phone, String status, String product, Pageable pageable);

    @Query(nativeQuery = true, name = "getListOrderOfPersonByStatus")
    List<OrderInfoDTO> getListOrderOfPersonByStatus(int status, long userId);

    @Query(nativeQuery = true, name = "userGetDetailById")
    OrderDetailDTO userGetDetailById(long id, long userId);

    int countByProductId(String id);

    // Lay don gan day nhat (bell notification admin). Limit 15.
    @Query(value = "SELECT o.id AS orderId, o.created_at AS createdAt, o.status AS status, o.quantity AS quantity, " +
            "COALESCE(u.full_name, o.receiver_name) AS buyerName, p.name AS productName " +
            "FROM orders o " +
            "LEFT JOIN users u ON u.id = o.buyer " +
            "LEFT JOIN product p ON p.id = o.product_id " +
            "ORDER BY o.created_at DESC LIMIT 15", nativeQuery = true)
    List<Object[]> getRecentOrdersRaw();

    // Lay status cua tat ca don cua user (de polling phia client).
    @Query(value = "SELECT id, status FROM orders WHERE buyer = ?1", nativeQuery = true)
    List<Object[]> getOrderStatusesByBuyer(long buyerId);


//        @Query("SELECT new com.phs.application.model.dto.OrderDetailDTO(" +
//                "o.id, " +
//                "o.totalPrice, " +
//                "p.price, " +
//                "o.receiverName, " +
//                "o.receiverPhone, " +
//                "o.receiverAddress, " +
//                "o.status, " +
//                "o.quantity, " +
//                "o.statusText, " +
//                "o.sizeVn, " +
//                "o.sizeUs, " +
//                "o.sizeCm, " +
//                "p.productName, " +
//                "p.productImg) " +
//                "FROM Order o " +
//                "JOIN o.product p " +
//                "where o.id = :id")
//        OrderDetailDTO getOrderDetail(long id, long userId);

}
