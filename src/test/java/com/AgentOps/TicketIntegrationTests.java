package com.AgentOps;

import com.AgentOps.entities.Order;
import com.AgentOps.entities.Ticket;
import com.AgentOps.entities.User;
import com.AgentOps.entities.enums.*;
import com.AgentOps.repositories.OrderRepository;
import com.AgentOps.repositories.TicketRepository;
import com.AgentOps.repositories.UserRepository;
import com.AgentOps.services.AuthSessionService;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TicketIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired UserRepository users;
    @Autowired OrderRepository orders;
    @Autowired TicketRepository tickets;
    @Autowired AuthSessionService sessions;
    @Autowired EntityManagerFactory entityManagerFactory;

    @Test
    void creationUsesOrderOwnerAndServerControlledDefaults() throws Exception {
        Account customer = account(UserRole.CUSTOMER);
        Order order = order(customer);
        String response = mvc.perform(post("/tickets").header("Authorization", bearer(customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":" + order.getId() + ",\"category\":\"DELIVERY\",\"customerId\":999,\"status\":\"CLOSED\",\"priority\":\"URGENT\",\"assignedToId\":999}"))
                .andExpect(status().isCreated()).andExpect(header().string("Location", startsWith("/tickets/")))
                .andExpect(jsonPath("$.customerId").value(customer.user().getId()))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.priority").value("NORMAL"))
                .andExpect(jsonPath("$.assignedToId").value(nullValue()))
                .andReturn().getResponse().getContentAsString();
        long id = mapper.readTree(response).get("id").asLong();
        mvc.perform(get("/tickets/" + id).header("Authorization", bearer(customer)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.orderId").value(order.getId()))
                .andExpect(jsonPath("$.customer").doesNotExist());
        assertEquals(order.getId(), tickets.findById(id).orElseThrow().getOrder().getId());
    }

    @Test
    void customerCannotCreateOrReadAnotherCustomersTicket() throws Exception {
        Account owner = account(UserRole.CUSTOMER);
        Account other = account(UserRole.CUSTOMER);
        Order order = order(owner);
        long ticketId = ticket(order, TicketCategory.ORDER).getId();
        mvc.perform(post("/tickets").header("Authorization", bearer(other)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":" + order.getId() + ",\"category\":\"ORDER\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/tickets/" + ticketId).header("Authorization", bearer(other)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/tickets?customerId=" + owner.user().getId()).header("Authorization", bearer(other)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void listScopesRowsAndCountsBeforePagination() throws Exception {
        Account owner = account(UserRole.CUSTOMER);
        Account other = account(UserRole.CUSTOMER);
        Order order = order(owner);
        long first = ticket(order, TicketCategory.PAYMENT).getId();
        ticket(order(other), TicketCategory.PAYMENT);
        long newest = ticket(order, TicketCategory.DELIVERY).getId();
        mvc.perform(get("/tickets?size=1").header("Authorization", bearer(owner)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id").value(newest))
                .andExpect(jsonPath("$.totalElements").value(2)).andExpect(jsonPath("$.totalPages").value(2));
        mvc.perform(get("/tickets?page=1&size=1").header("Authorization", bearer(owner)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].id").value(first));
        mvc.perform(get("/tickets?page=2&size=1").header("Authorization", bearer(owner)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content", hasSize(0)));
    }

    @Test
    void filtersCombineAndIncludeUnassignedTickets() throws Exception {
        Account owner = account(UserRole.CUSTOMER);
        Account support = account(UserRole.SUPPORT);
        Order order = order(owner);
        Ticket selected = ticket(order, TicketCategory.DELIVERY);
        selected.setPriority(TicketPriority.HIGH);
        selected.setStatus(TicketStatus.IN_PROGRESS);
        selected.setAssignedTo(support.user());
        tickets.saveAndFlush(selected);
        Ticket unassigned = ticket(order, TicketCategory.PAYMENT);
        String combined = "/tickets?status=IN_PROGRESS&priority=HIGH&category=DELIVERY&orderId=" + order.getId()
                + "&customerId=" + owner.user().getId() + "&assignedToId=" + support.user().getId() + "&unassigned=false";
        mvc.perform(get(combined).header("Authorization", bearer(support)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id").value(selected.getId()));
        mvc.perform(get("/tickets?unassigned=true").header("Authorization", bearer(owner)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id").value(unassigned.getId()));
        mvc.perform(get("/tickets?status=CLOSED").header("Authorization", bearer(owner)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void staffCanCreateReadAndPatchCustomerTickets() throws Exception {
        Account customer = account(UserRole.CUSTOMER);
        Order order = order(customer);
        for (UserRole role : new UserRole[] { UserRole.SUPPORT, UserRole.ADMIN }) {
            Account staff = account(role);
            String result = mvc.perform(post("/tickets").header("Authorization", bearer(staff))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"orderId\":" + order.getId() + ",\"category\":\"RETURN\"}"))
                    .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
            long id = mapper.readTree(result).get("id").asLong();
            mvc.perform(get("/tickets/" + id).header("Authorization", bearer(staff))).andExpect(status().isOk());
            mvc.perform(patch("/tickets/" + id).header("Authorization", bearer(staff))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\":\"IN_PROGRESS\",\"priority\":\"URGENT\",\"assignedToId\":" + staff.user().getId() + "}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                    .andExpect(jsonPath("$.priority").value("URGENT"))
                    .andExpect(jsonPath("$.assignedToId").value(staff.user().getId()));
            mvc.perform(patch("/tickets/" + id).header("Authorization", bearer(staff))
                            .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"RESOLVED\"}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.priority").value("URGENT"))
                    .andExpect(jsonPath("$.assignedToId").value(staff.user().getId()));
            mvc.perform(patch("/tickets/" + id).header("Authorization", bearer(staff))
                            .contentType(MediaType.APPLICATION_JSON).content("{\"unassign\":true}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.assignedToId").value(nullValue()));
        }
    }

    @Test
    void customersCannotPatchAndStaffCannotAssignCustomers() throws Exception {
        Account customer = account(UserRole.CUSTOMER);
        Account staff = account(UserRole.SUPPORT);
        long id = ticket(order(customer), TicketCategory.OTHER).getId();
        mvc.perform(patch("/tickets/" + id).header("Authorization", bearer(customer))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"CLOSED\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/tickets/" + id).header("Authorization", bearer(staff))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"assignedToId\":" + customer.user().getId() + "}"))
                .andExpect(status().isBadRequest());
        mvc.perform(patch("/tickets/" + id).header("Authorization", bearer(staff))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"assignedToId\":9223372036854775807}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void invalidBodiesFiltersAndIdsReturnProblemDetail() throws Exception {
        Account staff = account(UserRole.ADMIN);
        for (String body : new String[] { "{}", "{\"orderId\":0,\"category\":\"ORDER\"}",
                "{\"orderId\":1,\"category\":\"INVALID\"}", "{" }) {
            mvc.perform(post("/tickets").header("Authorization", bearer(staff)).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest()).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        }
        for (String body : new String[] { "{}", "{\"unassign\":false}", "{\"assignedToId\":0}",
                "{\"assignedToId\":1,\"unassign\":true}", "{\"status\":\"INVALID\"}", "{\"priority\":\"INVALID\"}" }) {
            mvc.perform(patch("/tickets/1").header("Authorization", bearer(staff)).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        for (String path : new String[] { "/tickets/0", "/tickets/not-an-id", "/tickets?page=-1", "/tickets?size=101",
                "/tickets?status=INVALID", "/tickets?priority=INVALID", "/tickets?category=INVALID",
                "/tickets?orderId=0", "/tickets?customerId=0", "/tickets?assignedToId=-1",
                "/tickets?assignedToId=1&unassigned=true", "/tickets?unassigned=invalid" }) {
            mvc.perform(get(path).header("Authorization", bearer(staff))).andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        }
    }

    @Test
    void missingResourcesAndAuthenticationAreHandled() throws Exception {
        Account staff = account(UserRole.SUPPORT);
        mvc.perform(get("/tickets/9223372036854775807").header("Authorization", bearer(staff))).andExpect(status().isNotFound());
        mvc.perform(patch("/tickets/9223372036854775807").header("Authorization", bearer(staff))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"CLOSED\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/tickets").header("Authorization", bearer(staff)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":9223372036854775807,\"category\":\"ORDER\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/tickets")).andExpect(status().isUnauthorized());
        mvc.perform(get("/tickets/1")).andExpect(status().isUnauthorized());
        mvc.perform(post("/tickets").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(patch("/tickets/1").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentUpdatesDoNotSilentlyOverwriteEachOther() {
        Account owner = account(UserRole.CUSTOMER);
        Order order = order(owner);
        Ticket ticket = ticket(order, TicketCategory.ORDER);
        try (var first = entityManagerFactory.createEntityManager();
             var second = entityManagerFactory.createEntityManager()) {
            first.getTransaction().begin();
            second.getTransaction().begin();
            Ticket firstCopy = first.find(Ticket.class, ticket.getId());
            Ticket secondCopy = second.find(Ticket.class, ticket.getId());
            firstCopy.setPriority(TicketPriority.HIGH);
            secondCopy.setPriority(TicketPriority.URGENT);
            first.getTransaction().commit();
            assertThrows(PersistenceException.class, () -> second.getTransaction().commit());
            if (second.getTransaction().isActive()) {
                second.getTransaction().rollback();
            }
            assertEquals(TicketPriority.HIGH, tickets.findById(ticket.getId()).orElseThrow().getPriority());
        } finally {
            tickets.deleteById(ticket.getId());
            orders.deleteById(order.getId());
            users.deleteById(owner.user().getId());
        }
    }

    private Account account(UserRole role) {
        User user = new User();
        user.setEmail(UUID.randomUUID() + "@example.com");
        user.setPasswordHash("ticket-test-placeholder");
        user.setRole(role);
        users.saveAndFlush(user);
        return new Account(user, sessions.createSession(user).accessToken());
    }

    private Order order(Account owner) {
        Order order = new Order();
        order.setCustomer(owner.user());
        order.setStatus(OrderStatus.PENDING);
        return orders.saveAndFlush(order);
    }

    private Ticket ticket(Order order, TicketCategory category) {
        Ticket ticket = new Ticket();
        ticket.setOrder(order);
        ticket.setCustomer(order.getCustomer());
        ticket.setCategory(category);
        ticket.setStatus(TicketStatus.OPEN);
        return tickets.saveAndFlush(ticket);
    }

    private String bearer(Account account) { return "Bearer " + account.token(); }
    private record Account(User user, String token) {
    }
}
