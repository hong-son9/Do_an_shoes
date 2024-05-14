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
            "AND product_id LIKE CONCAT('%',?5,'%')", nativeQuery = true)
    Page<Order> adminGetListOrder(String id, String name, String phone, String status, String product, Pageable pageable);

    @Query(nativeQuery = true, name = "getListOrderOfPersonByStatus")
    List<OrderInfoDTO> getListOrderOfPersonByStatus(int status, long userId);

    @Query(nativeQuery = true, name = "userGetDetailById")
    OrderDetailDTO userGetDetailById(long id, long userId);

    int countByProductId(String id);


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
