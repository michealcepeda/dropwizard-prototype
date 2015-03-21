package com.sandeep.prototypes.dropwizard.resources;

import java.util.HashMap;
import java.util.Map;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import com.newrelic.api.agent.NewRelic;
import com.sandeep.prototypes.address.entity.Address;
import com.sandeep.prototypes.dropwizard.client.AddressClient;
import com.sandeep.prototypes.dropwizard.client.AddressServiceException;
import com.sandeep.prototypes.dropwizard.client.AddressServiceException.AddressNotFoundException;
import com.sandeep.prototypes.dropwizard.client.AddressServiceException.AddressTemporarilyUnavailableException;
import com.sandeep.prototypes.dropwizard.client.AddressServiceException.RequestTimeout;
import com.sandeep.prototypes.dropwizard.core.Person;
import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;
import com.wordnik.swagger.annotations.ApiResponse;
import com.wordnik.swagger.annotations.ApiResponses;

@Path("/person")
@Consumes("application/json")
@Api(value = "/person", description = "Person Management API")
public class PersonResource {
  private static final String SUCCESS_METRIC_NAME = "Adobe/Social/Person/Address/200[count]";
  private static final String NOT_FOUND_METRIC_NAME = "Adobe/Social/Person/Address/404[count]";
  private static final String SERVICE_UNAVAILABLE_METRIC_NAME =
      "Adobe/Social/Person/Address/503[count]";
  private static final String TIMEOUT_METRIC_NAME = "Adobe/Social/Person/Address/408[count]";
  private static final String FAILED_METRIC_NAME = "Adobe/Social/Person/Address/500[count]";
  private final String message;
  private static Map<Integer, Person> people = new HashMap<Integer, Person>();
  private AddressClient addressClient;
  private static final Logger logger = LoggerFactory.getLogger(PersonResource.class);

  @Inject
  public PersonResource(String message, AddressClient client) {
    this.message = message;
    this.addressClient = client;
  }

  private AddressClient getAddressClient() {
    return addressClient;
  }

  @GET
  @Timed
  @Produces(MediaType.APPLICATION_JSON)
  @Path("/{id}")
  @ApiOperation(value = "Find person by id",
      notes = "Returns a person when id > 1 or nonintegers will simulate API error conditions",
      response = Person.class)
  @ApiResponses(value = {@ApiResponse(code = 400, message = "Invalid Id supplied"),
      @ApiResponse(code = 404, message = "Person not found")})
  public Response getPerson(@PathParam("id") Integer id) {
    if (id < 1) {
      return Response.status(Status.BAD_REQUEST).type(MediaType.APPLICATION_JSON).build();
    }
    Address address = null;
    try {
      address = getAddressClient().getAddress(1);
      NewRelic.incrementCounter(SUCCESS_METRIC_NAME);
    } catch (AddressNotFoundException e) {
      NewRelic.incrementCounter(NOT_FOUND_METRIC_NAME);
      logger.error("Address not found. Reason {}", e.getMessage());
    } catch (AddressTemporarilyUnavailableException e) {
      NewRelic.incrementCounter(SERVICE_UNAVAILABLE_METRIC_NAME);
      logger.error("Address service unavailable {}", e.getMessage());
    } catch (RequestTimeout e) {
      NewRelic.incrementCounter(TIMEOUT_METRIC_NAME);
      logger.error("Address timeout {}", e.getMessage());
    } catch (AddressServiceException e) {
      NewRelic.incrementCounter(FAILED_METRIC_NAME);
      logger.error("Address lookup failed {}", e.getMessage());
    }

    Person person = people.get(id);
    if (person != null) {
      person.setAddress(address);
      person.setMessage(String.format(message, person.getFirstName(), person.getLastName()));
      return Response.status(Status.OK).type(MediaType.APPLICATION_JSON).entity(person).build();
    }
    return Response.status(Status.NOT_FOUND)
        .entity(String.format("Person with id %s not found", id)).type(MediaType.APPLICATION_JSON)
        .build();
  }

  @POST
  @ApiOperation(value = "Add a new person")
  @ApiResponses(value = {@ApiResponse(code = 405, message = "Invalid input")})
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public Response addUser(Person person) {
    people.put(person.getId(), person);
    return Response.status(Status.OK).type(MediaType.APPLICATION_JSON).entity(person).build();
  }
}
