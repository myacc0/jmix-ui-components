package uz.kapitalbank.umida;

import uz.kapitalbank.umida.entity.orgstructure.OrgStructureSubdivision;
import io.jmix.core.UnconstrainedDataManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

@SpringBootTest
public class DataManagerTests {

    @Autowired
    UnconstrainedDataManager dataManager;

    @Test
    void test_like_en() {
        String searchString = "bank";
        List<OrgStructureSubdivision> subdivisions = dataManager.load(OrgStructureSubdivision.class)
                .query("select c from umida_OrgStructureSubdivision c where lower(c.name) like :name")
                .parameter("name", "%" + searchString.toLowerCase() + "%")
                .list();

        System.out.println("size: " + subdivisions.size());
        subdivisions.forEach(System.out::println);
    }

    @Test
    void test_like_ru() {
        String searchString = "Отдел";
        List<OrgStructureSubdivision> subdivisions = dataManager.load(OrgStructureSubdivision.class)
                .query("select e from umida_OrgStructureSubdivision e where e.name like :name")
                .parameter("name", "(?i)%" + searchString + "%")
                .list();

        System.out.println("size: " + subdivisions.size());
        subdivisions.forEach(System.out::println);
    }

}
