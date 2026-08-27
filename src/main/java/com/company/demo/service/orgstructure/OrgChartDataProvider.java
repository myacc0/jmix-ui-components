package com.company.demo.service.orgstructure;

import com.company.demo.dto.orgstructure.D3OrgChartCsvItem;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencsv.bean.CsvToBean;
import com.opencsv.bean.CsvToBeanBuilder;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStreamReader;
import java.io.Reader;
import java.util.List;

@Service
public class OrgChartDataProvider {

    public String getNodesJson(List<D3OrgChartCsvItem> nodes) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            return objectMapper.writeValueAsString(nodes);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Json load error", e);
        }
    }

    public List<D3OrgChartCsvItem> produceFromCsv() {
        return parseCsv("data/d3-orgchart-demo-data.csv");
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
