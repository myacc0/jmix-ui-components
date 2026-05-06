package com.company.demo;

import com.company.demo.entity.Department;
import io.jmix.core.UnconstrainedDataManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.charset.StandardCharsets;
import java.util.List;

@SpringBootTest
public class DataManagerTests {

    @Autowired
    UnconstrainedDataManager dataManager;

    @Test
    void test_like_en() {
        String searchString = "bank";
        List<Department> departmentList = dataManager.load(Department.class)
                .query("select c from demo_Department c where lower(c.name) like :name")
                .parameter("name", "%" + searchString.toLowerCase() + "%")
                .list();

        System.out.println("size: " + departmentList.size());
        departmentList.forEach(System.out::println);
    }

    @Test
    void test_like_ru() {
        String searchString = "Отдел";
        List<Department> departmentList = dataManager.load(Department.class)
                .query("select e from demo_Department e where e.name like :name")
                .parameter("name", "(?i)%" + searchString + "%")
                .list();

        System.out.println("size: " + departmentList.size());
        departmentList.forEach(System.out::println);
    }

}
