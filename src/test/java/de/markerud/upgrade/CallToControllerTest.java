package de.markerud.upgrade;

import io.restassured.RestAssured;
import org.mockserver.model.HttpRequest;

import static org.apache.http.HttpStatus.SC_OK;

public class CallToControllerTest extends TestBase {

    @Override
    protected HttpRequest sendRequest() {
        mockBackendRespondOK();
        RestAssured
                .given().when()
                .port(serverPort)
                .get("/question-controller")
                .then()
                .statusCode(SC_OK);
        return getSentRequest();
    }

}
