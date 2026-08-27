package com.company.demo.service.orgstructure;

import com.company.demo.dto.orgstructure.D3OrgChartCsvItem;
import com.company.demo.entity.orgstructure.Position;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencsv.bean.CsvToBean;
import com.opencsv.bean.CsvToBeanBuilder;
import io.jmix.core.DataManager;
import io.jmix.core.Sort;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStreamReader;
import java.io.Reader;
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

    public String getNodesJsonFromCsv() {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            return objectMapper.writeValueAsString(
                    parseCsv("data/d3-orgchart-demo-data.csv"));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Json load error", e);
        }
    }

    private List<D3OrgChartCsvItem> parseCsv(String path) {
        try {
            ClassPathResource resource = new ClassPathResource(path);

            try (Reader reader = new InputStreamReader(resource.getInputStream())) {
                CsvToBean<D3OrgChartCsvItem> csvToBean = new CsvToBeanBuilder<D3OrgChartCsvItem>(reader)
                        .withType(D3OrgChartCsvItem.class)
                        .withIgnoreLeadingWhiteSpace(true)
                        .build();

                return csvToBean.parse();
            }

        } catch (Exception e) {
            throw new RuntimeException("Csv load error", e);
        }
    }

}
