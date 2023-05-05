package uk.ac.ebi.protvar.fetcher;

import java.net.URI;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import uk.ac.ebi.protvar.converter.ProteinAPI2ProteinConverter;
import uk.ac.ebi.protvar.exception.ServiceException;
import uk.ac.ebi.protvar.model.api.DataServiceProtein;
import uk.ac.ebi.protvar.model.api.ProteinFeature;
import uk.ac.ebi.protvar.model.response.Protein;
import uk.ac.ebi.protvar.repo.UniprotAPIRepo;
import uk.ac.ebi.protvar.utils.FetcherUtils;
import uk.ac.ebi.protvar.utils.ProteinHelper;

@Service
@AllArgsConstructor
public class ProteinFetcher {

	private static final Logger logger = LoggerFactory.getLogger(ProteinFetcher.class);

	private final Map<String, DataServiceProtein> cache2 = new ConcurrentHashMap<>();

	private UniprotAPIRepo uniprotAPIRepo;
	private ProteinAPI2ProteinConverter converter;


	/**
	 * Prefetch data from Proteins API and cache in application for
	 * subsequent retrieval.
	 */
	public void prefetch(Set<String> accessions) {
		Set<String> cachedAccessions = cache2.keySet();

		// check accession in ProteinsCache
		Set<String> notCachedAccessions = accessions.stream().filter(Predicate.not(cachedAccessions::contains)).collect(Collectors.toSet());
		List<Set<String>> notCachedAccessionsPartitions = FetcherUtils.partitionSet(notCachedAccessions, FetcherUtils.PARTITION_SIZE);

		notCachedAccessionsPartitions.stream().parallel().forEach(accessionsSet -> {
			DataServiceProtein[] dataServiceProteins = uniprotAPIRepo.getProtein(String.join(",", accessionsSet));
			for (DataServiceProtein dsp : dataServiceProteins) {
				logger.info("Caching Protein: {}", dsp.getAccession());
				cache2.put(dsp.getAccession(), dsp);
			}
		});
	}

	/**
	 * 
	 * @return - Map of accession and Protein. Empty map if no Protein found
	 */
	public Protein fetch(String accession, int position) {
		if (!StringUtils.isEmpty(accession)) {
			DataServiceProtein dsp = null;
			if (cache2.containsKey(accession)) {
				dsp = cache2.get(accession);
			} else {
				DataServiceProtein[] dataServiceProteins = uniprotAPIRepo.getProtein(accession);
				if (dataServiceProteins != null && dataServiceProteins.length > 0) {
					dsp = dataServiceProteins[0];
					cache2.put(accession, dsp);
				}
			}
			if (dsp != null) {
				Protein protein = converter.fetch(dsp);
				List<ProteinFeature> features = ProteinHelper.filterFeatures(protein.getFeatures(), position, position);
				protein.setFeatures(features);
				protein.setPosition(position);
				return protein;
			}
		}
		return null;
	}

}
