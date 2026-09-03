package com.company.demo.starrocks;

import com.company.demo.entity.starrocks.Order;
import com.company.demo.entity.starrocks.Product;
import io.jmix.core.UnconstrainedDataManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that the entities in the {@code starrocks} data store actually map onto the
 * physical StarRocks tables — the column names, the DECIMAL(20,4) money columns, the
 * VARCHAR(36) uuid-as-text ids, and the orders -> products reference.
 * <p>
 * Reads only; it asserts against the rows mirrored from postgres-dwh and writes nothing.
 * Requires the jmix-starrocks container to be running.
 */
@SpringBootTest
public class StarrocksEntityMappingTests {

    private static final String SAMSUNG_ID = "7da61f41-fd64-448a-8080-626996592307";

    @Autowired
    UnconstrainedDataManager dataManager;

    @Test
    void loads_products_from_starrocks_store() {
        List<Product> products = dataManager.load(Product.class).all().list();

        assertThat(products).isNotEmpty();
        assertThat(products).allSatisfy(p -> {
            assertThat(p.getId()).isNotBlank();
            assertThat(p.getName()).isNotBlank();
            assertThat(p.getPrice()).isNotNull();
            assertThat(p.getCategory()).isNotNull();
            assertThat(p.getQuantity()).isNotNull();
            assertThat(p.getCreatedAt()).isNotNull();
        });
    }

    @Test
    void maps_every_product_column_including_cyrillic_and_decimal_scale() {
        Optional<Product> found = dataManager.load(Product.class).id(SAMSUNG_ID).optional();

        assertThat(found).isPresent();
        Product product = found.get();
        // Cyrillic must survive the MySQL wire protocol intact
        assertThat(product.getName()).isEqualTo("Samsung Galaxy A17 6/128GB Серый");
        assertThat(product.getPrice()).isEqualByComparingTo(new BigDecimal("3199000"));
        assertThat(product.getSale()).isEqualByComparingTo(new BigDecimal("100000"));
        assertThat(product.getCategory()).isEqualTo(1);
        assertThat(product.getQuantity()).isEqualTo(200);
        assertThat(product.getDescription()).isNotBlank();
        assertThat(product.getCreatedAt()).isNotNull();
        // DECIMAL(20,4) must arrive with its scale, not as a rounded integer
        assertThat(product.getPrice().scale()).isEqualTo(4);
    }

    @Test
    void jpql_query_against_starrocks_entity_name_works() {
        List<Product> products = dataManager.load(Product.class)
                .query("select p from demo_StarrocksProduct p where p.category = :category")
                .parameter("category", 1)
                .list();

        assertThat(products).isNotEmpty();
        assertThat(products).allMatch(p -> p.getCategory() == 1);
    }

    @Test
    void loads_orders_and_resolves_the_product_reference() {
        List<Order> orders = dataManager.load(Order.class).all().list();
        assertThat(orders).isNotEmpty();

        Optional<Order> found = dataManager.load(Order.class).id(1).optional();
        assertThat(found).isPresent();

        Order order = found.get();
        assertThat(order.getTotalSum()).isEqualByComparingTo(new BigDecimal("3099000"));
        assertThat(order.getSaleSum()).isEqualByComparingTo(new BigDecimal("100000"));
        assertThat(order.getQuantity()).isEqualTo(1);
        assertThat(order.getPaymentMethod()).isEqualTo("cash");
        assertThat(order.getCustomerEmail()).isEqualTo("customer1@gmail.com");
        assertThat(order.getCustomerName()).isEqualTo("customer1");
        assertThat(order.getOrderDate()).isNotNull();
        // nullable columns must come back as null, not as empty strings
        assertThat(order.getCustomerPhone()).isNull();
        assertThat(order.getNotes()).isNull();
    }

    @Test
    void order_to_product_reference_crosses_the_varchar_foreign_key() {
        Order order = dataManager.load(Order.class)
                .id(1)
                .fetchPlanProperties("product.name", "totalSum")
                .one();

        assertThat(order.getProduct()).isNotNull();
        assertThat(order.getProduct().getId()).isEqualTo(SAMSUNG_ID);
        assertThat(order.getProduct().getName()).isEqualTo("Samsung Galaxy A17 6/128GB Серый");
    }
}
