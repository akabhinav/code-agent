package com.joz.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.joz.common.exception.JozException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** SQLite-backed storage for execution plans. */
public class PlanStore implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(PlanStore.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    private final Connection connection;

    public PlanStore(Connection connection) {
        this.connection = connection;
        initSchema();
    }

    private void initSchema() {
        try (var stmt = connection.createStatement()) {
            stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS plans (
                        id TEXT PRIMARY KEY,
                        session_id TEXT NOT NULL,
                        title TEXT NOT NULL,
                        steps TEXT NOT NULL,
                        status TEXT NOT NULL,
                        created_at TEXT NOT NULL,
                        updated_at TEXT NOT NULL
                    )""");
        } catch (SQLException e) {
            throw new JozException.SessionException("Failed to create plans table", e);
        }
    }

    /** Creates a new plan. */
    public void createPlan(Plan plan) {
        try {
            var stepsJson = mapper.writeValueAsString(plan.steps());
            try (var stmt = connection.prepareStatement(
                    "INSERT INTO plans (id, session_id, title, steps, status, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?)")) {
                stmt.setString(1, plan.id());
                stmt.setString(2, plan.sessionId());
                stmt.setString(3, plan.title());
                stmt.setString(4, stepsJson);
                stmt.setString(5, plan.status());
                var now = Instant.now().toString();
                stmt.setString(6, now);
                stmt.setString(7, now);
                stmt.executeUpdate();
            }
        } catch (JsonProcessingException | SQLException e) {
            throw new JozException.SessionException("Failed to create plan", e);
        }
    }

    /** Updates a plan's status and steps. */
    public void updatePlan(String planId, String status, List<PlanStep> steps) {
        try {
            var stepsJson = mapper.writeValueAsString(steps);
            try (var stmt = connection.prepareStatement(
                    "UPDATE plans SET status = ?, steps = ?, updated_at = ? WHERE id = ?")) {
                stmt.setString(1, status);
                stmt.setString(2, stepsJson);
                stmt.setString(3, Instant.now().toString());
                stmt.setString(4, planId);
                stmt.executeUpdate();
            }
        } catch (JsonProcessingException | SQLException e) {
            throw new JozException.SessionException("Failed to update plan", e);
        }
    }

    /** Loads a plan by ID. */
    @SuppressWarnings("unchecked")
    public Optional<Plan> getPlan(String planId) {
        try (var stmt = connection.prepareStatement(
                "SELECT * FROM plans WHERE id = ?")) {
            stmt.setString(1, planId);
            var rs = stmt.executeQuery();
            if (rs.next()) {
                List<PlanStep> steps = mapper.readValue(rs.getString("steps"),
                        mapper.getTypeFactory().constructCollectionType(List.class, PlanStep.class));
                return Optional.of(new Plan(
                        rs.getString("id"),
                        rs.getString("session_id"),
                        rs.getString("title"),
                        steps,
                        rs.getString("status")));
            }
            return Optional.empty();
        } catch (Exception e) {
            throw new JozException.SessionException("Failed to load plan", e);
        }
    }

    /** Lists plans for a session. */
    @SuppressWarnings("unchecked")
    public List<Plan> listPlans(String sessionId) {
        var plans = new ArrayList<Plan>();
        try (var stmt = connection.prepareStatement(
                "SELECT * FROM plans WHERE session_id = ? ORDER BY created_at DESC")) {
            stmt.setString(1, sessionId);
            var rs = stmt.executeQuery();
            while (rs.next()) {
                List<PlanStep> steps = mapper.readValue(rs.getString("steps"),
                        mapper.getTypeFactory().constructCollectionType(List.class, PlanStep.class));
                plans.add(new Plan(
                        rs.getString("id"),
                        rs.getString("session_id"),
                        rs.getString("title"),
                        steps,
                        rs.getString("status")));
            }
        } catch (Exception e) {
            throw new JozException.SessionException("Failed to list plans", e);
        }
        return plans;
    }

    @Override
    public void close() {
        // Connection is managed externally
    }

    /** A structured execution plan. */
    public record Plan(String id, String sessionId, String title, List<PlanStep> steps, String status) {}

    /** A single step in a plan. */
    public record PlanStep(String description, String status, String result) {}
}
