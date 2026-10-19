package com.bookmgmt.loan_service.client;

import com.bookmgmt.loan_service.exception.LoanException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;



@Component
public class BookClient {

	private static final String KEY_HEADER = "X-Internal-Key";

	private final RestClient client;
	private final String internalKey;

	public BookClient(@Value("${book-service.url}") String baseUrl,
			@Value("${app.internal.key}") String internalKey,
			@Value("${book-service.timeout-ms:3000}") int timeoutMs) {
		SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
		factory.setConnectTimeout(timeoutMs);
		factory.setReadTimeout(timeoutMs);
		this.client = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
		this.internalKey = internalKey;
	}

	/** Atomically takes one copy. 404 = no such book, 409 = no copies left. */
	public BookSnapshot reserve(Integer bookId) {
		try {
			return client.post()
					.uri("/internal/books/{id}/reserve", bookId)
					.header(KEY_HEADER, internalKey)
					.retrieve()
					.body(BookSnapshot.class);
		} catch (RestClientResponseException e) {
			int status = e.getStatusCode().value();
			if (status == 404)
				throw new LoanException(HttpStatus.NOT_FOUND, "Book not found");
			if (status == 409)
				throw new LoanException(HttpStatus.CONFLICT, "No copies of this book are available right now");
			throw unavailable(e);
		} catch (RestClientException e) {
			throw unavailable(e);
		}
	}

	/** Gives one copy back. If the book was deleted meanwhile there is nothing to restore. */
	public void release(Integer bookId) {
		try {
			client.post()
					.uri("/internal/books/{id}/release", bookId)
					.header(KEY_HEADER, internalKey)
					.retrieve()
					.toBodilessEntity();
		} catch (RestClientResponseException e) {
			if (e.getStatusCode().value() == 404)
				return;
			throw unavailable(e);
		} catch (RestClientException e) {
			throw unavailable(e);
		}
	}

	private LoanException unavailable(Exception cause) {
		LoanException ex = new LoanException(HttpStatus.SERVICE_UNAVAILABLE,
				"Book service is unavailable, please try again later");
		ex.initCause(cause);
		return ex;
	}
}
