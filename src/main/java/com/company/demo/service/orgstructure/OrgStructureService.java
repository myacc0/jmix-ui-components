package com.company.demo.service.orgstructure;

import com.company.demo.entity.orgstructure.Position;
import io.jmix.core.DataManager;
import io.jmix.core.Sort;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class OrgStructureService {
    private final DataManager dataManager;

    public OrgStructureService(DataManager dataManager) {
        this.dataManager = dataManager;
    }

    public List<Position> getPositions(String searchText, int size) {
        return dataManager.load(Position.class)
                .query("select p from demo_Position p where p.employee.fullName like :name")
                .parameter("name", "(?i)%" + searchText + "%")
                .sort(Sort.by(Sort.Order.asc("employee.fullName")))
                .firstResult(0)
                .maxResults(size)
                .list();
    }

}
