package uk.ac.ebi.protvar.fetcher;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.deser.impl.ValueInjector;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import uk.ac.ebi.protvar.converter.VariationAPI2VariationConverter;
import uk.ac.ebi.protvar.model.api.DataServiceProtein;
import uk.ac.ebi.protvar.model.api.DataServiceVariation;
import uk.ac.ebi.protvar.model.api.Feature;
import uk.ac.ebi.protvar.model.api.ProteinFeature;
import uk.ac.ebi.protvar.model.response.PopulationObservation;
import uk.ac.ebi.protvar.model.response.Variation;
import uk.ac.ebi.protvar.repo.UniprotAPIRepo;
import uk.ac.ebi.protvar.utils.FetcherUtils;

import static uk.ac.ebi.protvar.utils.Commons.notNullNotEmpty;
import static uk.ac.ebi.protvar.utils.Commons.nullOrEmpty;

@Service
@AllArgsConstructor
public class VariationFetcher {

	private static final Logger logger = LoggerFactory.getLogger(VariationFetcher.class);

	private final Map<String, List<Variation>> cache2 = new ConcurrentHashMap<>();

	private UniprotAPIRepo uniprotAPIRepo;
	private VariationAPI2VariationConverter converter;


	/**
	 * Prefetch data from Variation API and cache in application for
	 * subsequent retrieval.
	 */
	public void prefetch(Set<String> accessionLocations) {
		Set<String> cached = cache2.keySet();

		// check accession-location in variation cache
		Set<String> notCached = accessionLocations.stream().filter(Predicate.not(cached::contains)).collect(Collectors.toSet());
		List<Set<String>> notCachedPartitions = FetcherUtils.partitionSet(notCached, FetcherUtils.PARTITION_SIZE);

		notCachedPartitions.stream().parallel().forEach(accessionsSet -> {
			cacheAPIResponse(accessionsSet);
		});
	}

	private void cacheAPIResponse(Set<String> accessionLocations) {
		//Set<String> accessionLocations
		Map<String, List<Variation>> variationMap = new ConcurrentHashMap<>();
		for (String accessionLocation : accessionLocations) {
			variationMap.put(accessionLocation, new ArrayList<>());
		}

		try {
			DataServiceVariation[] dataServiceVariations = uniprotAPIRepo.getVariationAccessionLocations(String.join("|", accessionLocations));
			if (dataServiceVariations != null && dataServiceVariations.length > 0) {

				for (DataServiceVariation dsv : dataServiceVariations) {
					//Map<String, List<Variation>> variationMap = new ConcurrentHashMap<>();
					dsv.getFeatures().stream()
							.filter(Objects::nonNull)
							.map(converter::convert)
							.filter(v -> notNullNotEmpty(v.getAlternativeSequence()))
							.filter(v -> notNullNotEmpty(v.getWildType()))
							.forEach(v -> {
								String key = dsv.getAccession() + ":" + v.getBegin();
								if (variationMap.containsKey(key)) {
									List<Variation> vs = variationMap.get(key);
									vs.add(v);
								} else {
									variationMap.put(key, new ArrayList<>(Arrays.asList(v)));
								}

					});
					if (variationMap.isEmpty()) {

					}
					logger.info("Caching Variation: {}", String.join(",", variationMap.keySet()));
					// update cache
					cache2.putAll(variationMap);
				}
			}
		}
		catch (Exception ex) {
			logger.error(ex.getMessage());
		}
	}

	public List<Variation> fetch(String uniprotAccession, int proteinLocation) {
		String key = uniprotAccession + ":" + proteinLocation;
		if (cache2.containsKey(key))
			return cache2.get(key);

		cacheAPIResponse(new HashSet<>(Arrays.asList(key)));

		if (cache2.containsKey(key))
			return cache2.get(key);

		return Collections.emptyList();
	}

	private boolean isWithinLocationRange(long begin, long end, Feature feature) {
		return (begin >= feature.getBegin() && end <= feature.getEnd())
				|| (begin >= feature.getBegin() && begin <= feature.getEnd())
				|| (end >= feature.getBegin() && end <= feature.getEnd());
	}

	public PopulationObservation fetchPopulationObservation(String accession, int proteinLocation) {
		List<Variation> variations = fetch(accession, proteinLocation);

		PopulationObservation populationObservation = new PopulationObservation();
		populationObservation.setProteinColocatedVariant(variations);
		return populationObservation;
	}

}
