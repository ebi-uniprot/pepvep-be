package uk.ac.ebi.uniprot.variation.api;

import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Repository;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.DefaultUriBuilderFactory;
import org.springframework.web.util.UriBuilder;
import uk.ac.ebi.uniprot.variation.model.DataServiceVariation;
import uk.ac.ebi.uniprot.common.Common;

@Repository("variationAPIImpl")
@AllArgsConstructor
public class VariationAPIImpl implements VariationAPI {

    private static final Logger logger = LoggerFactory.getLogger(VariationAPIImpl.class);


    private RestTemplate variantRestTemplate;

    @Override
    public DataServiceVariation[] getVariationByParam(String paramValue, String pathParam) {
        logger.info("Calling variation: {}", paramValue);
        DefaultUriBuilderFactory handler = (DefaultUriBuilderFactory) this.variantRestTemplate.getUriTemplateHandler();
        UriBuilder uriBuilder = handler.builder().path(pathParam).path(paramValue);

        ResponseEntity<DataServiceVariation[]> response = this.variantRestTemplate.getForEntity(uriBuilder.build(),
                DataServiceVariation[].class);
        return response.getBody();
    }

    @Override
    public DataServiceVariation[] getVariationByAccession(String accession, String location) {
        logger.info("Calling colocated variation: {}", accession);
        DefaultUriBuilderFactory handler = (DefaultUriBuilderFactory) this.variantRestTemplate.getUriTemplateHandler();
        MultiValueMap<String, String> queryParams = new LinkedMultiValueMap<>();
        queryParams.add(Common.PARAM_TAXID, Common.TAX_ID_HUMAN);
        queryParams.add(Common.PARAM_ACCESSION, accession);
        if (location != null) {
            queryParams.add(Common.PARAM_LOCATION, location);
        }
        UriBuilder uriBuilder = handler.builder().queryParams(queryParams);
        ResponseEntity<DataServiceVariation[]> response = this.variantRestTemplate.getForEntity(uriBuilder.build(),
                DataServiceVariation[].class);
        return response.getBody();
    }

}
