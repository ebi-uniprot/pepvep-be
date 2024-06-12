package uk.ac.ebi.protvar.model.response;

import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PagedMappingResponse {
	private MappingResponse content;

	private String resultId;

	private int page;

	private int pageSize;

	private long totalItems;

	private int totalPages;

	private boolean last;
}
