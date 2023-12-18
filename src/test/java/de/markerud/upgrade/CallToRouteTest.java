package de.markerud.upgrade;

import io.restassured.RestAssured;
import org.mockserver.model.HttpRequest;

import static org.apache.http.HttpStatus.SC_OK;

public class CallToRouteTest extends TestBase {

    @Override
    protected HttpRequest sendRequest() {
        mockBackendRespondOK();
        RestAssured
                .given().when()
                .port(serverPort)
                .get("/question-route")
                .then()
                .statusCode(SC_OK);
        return getSentRequest();
    }

}
