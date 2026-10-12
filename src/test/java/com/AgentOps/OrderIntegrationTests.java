package com.AgentOps;

import com.AgentOps.entities.User;
import com.AgentOps.entities.enums.UserRole;
import com.AgentOps.repositories.OrderRepository;
import com.AgentOps.repositories.UserRepository;
import com.AgentOps.services.AuthSessionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class OrderIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired UserRepository users;
    @Autowired OrderRepository orders;
    @Autowired AuthSessionService sessions;
    @Autowired PasswordEncoder passwords;

    @Test
    void customerCanCreateAndReadPersistedOrder() throws Exception {
        Account customer = account(UserRole.CUSTOMER);
        long id = create(customer, "{\"status\":\"DELIVERED\"}");
        assertEquals(customer.id(), orders.findById(id).orElseThrow().getCustomer().getId());
        mvc.perform(get("/orders/" + id).header("Authorization", bearer(customer)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.customerId").value(customer.id()))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.updatedAt").isNotEmpty())
                .andExpect(jsonPath("$.customer").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void customerListIsScopedBeforePaginationAndTotals() throws Exception {
        Account customer = account(UserRole.CUSTOMER);
        Account other = account(UserRole.CUSTOMER);
        long first = create(customer, "{}");
        create(other, "{}");
        long newest = create(customer, "{\"customerId\":" + customer.id() + "}");
        mvc.perform(get("/orders?page=0&size=1").header("Authorization", bearer(customer)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id").value(newest))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.page").value(0));
        mvc.perform(get("/orders?page=1&size=1").header("Authorization", bearer(customer)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].id").value(first));
        mvc.perform(get("/orders?page=2&size=1").header("Authorization", bearer(customer)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content", hasSize(0)))
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void otherCustomersCannotReadOrCreateAnotherCustomersOrder() throws Exception {
        Account owner = account(UserRole.CUSTOMER);
        Account other = account(UserRole.CUSTOMER);
        long id = create(owner, "{}");
        mvc.perform(get("/orders/" + id).header("Authorization", bearer(other)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        mvc.perform(post("/orders").header("Authorization", bearer(other))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"customerId\":" + owner.id() + "}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/orders").header("Authorization", bearer(other)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content", hasSize(0)))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void adminAndSupportCanCreateAndReadCustomerOrders() throws Exception {
        Account customer = account(UserRole.CUSTOMER);
        for (UserRole role : new UserRole[] { UserRole.ADMIN, UserRole.SUPPORT }) {
            Account staff = account(role);
            long id = create(staff, "{\"customerId\":" + customer.id() + "}");
            mvc.perform(get("/orders/" + id).header("Authorization", bearer(staff)))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.customerId").value(customer.id()));
            mvc.perform(get("/orders").header("Authorization", bearer(staff)))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.content[*].id", hasItem((int) id)));
        }
    }

    @Test
    void staffCannotCreateOrderWithoutCustomerOrForStaffAccount() throws Exception {
        Account staff = account(UserRole.ADMIN);
        for (String body : new String[] { "{}", "{\"customerId\":" + staff.id() + "}" }) {
            mvc.perform(post("/orders").header("Authorization", bearer(staff))
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(post("/orders").header("Authorization", bearer(staff))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"customerId\":9223372036854775807}"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/orders/9223372036854775807").header("Authorization", bearer(staff)))
                .andExpect(status().isNotFound());
    }

    @Test
    void invalidBodyIdsAndPaginationReturnProblemDetails() throws Exception {
        Account customer = account(UserRole.CUSTOMER);
        mvc.perform(post("/orders").header("Authorization", bearer(customer))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"customerId\":0}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors").isArray());
        mvc.perform(post("/orders").header("Authorization", bearer(customer))
                        .contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest());
        for (String path : new String[] { "/orders/0", "/orders/-1", "/orders/not-an-id",
                "/orders?page=-1", "/orders?size=0", "/orders?size=101", "/orders?page=invalid" }) {
            mvc.perform(get(path).header("Authorization", bearer(customer)))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        }
    }

    @Test
    void allOrderEndpointsRequireAuthentication() throws Exception {
        mvc.perform(post("/orders").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/orders")).andExpect(status().isUnauthorized());
        mvc.perform(get("/orders/1")).andExpect(status().isUnauthorized());
    }

    private long create(Account account, String body) throws Exception {
        String result = mvc.perform(post("/orders").header("Authorization", bearer(account))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andExpect(header().string("Location", startsWith("/orders/")))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(result).get("id").asLong();
    }

    private Account account(UserRole role) {
        User user = new User();
        user.setEmail(UUID.randomUUID() + "@example.com");
        user.setPasswordHash(passwords.encode("password123"));
        user.setRole(role);
        users.saveAndFlush(user);
        return new Account(user.getId(), sessions.createSession(user).accessToken());
    }

    private String bearer(Account account) {
        return "Bearer " + account.token();
    }

    private record Account(Long id, String token) {
    }
}
