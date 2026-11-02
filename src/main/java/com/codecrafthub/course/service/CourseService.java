package com.codecrafthub.course.service;

import com.codecrafthub.course.model.Course;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Owns course operations and saves the course list in courses.json.
 * Synchronized methods keep two requests in this app from overwriting each other.
 */
@Service
public class CourseService {

    private static final List<String> ALLOWED_STATUSES =
            List.of("Not Started", "In Progress", "Completed");

    private final ObjectMapper objectMapper;
    private final Path dataFile;

    public CourseService(
            ObjectMapper objectMapper,
            @Value("${codecrafthub.data-file:courses.json}") String dataFile) {
        this.objectMapper = objectMapper;
        this.dataFile = Path.of(dataFile).toAbsolutePath();
    }

    // Create the JSON file on first launch so the app is ready to use immediately.
    @PostConstruct
    public void initializeFile() {
        try {
            Files.createDirectories(dataFile.getParent());
            if (Files.notExists(dataFile)) {
                Files.writeString(dataFile, "[]");
            }
        } catch (IOException exception) {
            throw new CourseFileException("Could not create courses.json", exception);
        }
    }

    public synchronized List<Course> getAllCourses() {
        return readCourses();
    }

    public synchronized Course getCourseById(long id) {
        return findCourse(readCourses(), id)
                .orElseThrow(() -> new CourseNotFoundException(id));
    }

    public synchronized Course addCourse(Course course) {
        validateCourse(course);
        List<Course> courses = readCourses();

        long nextId = courses.stream()
                .map(Course::getId)
                .filter(id -> id != null)
                .max(Comparator.naturalOrder())
                .orElse(0L) + 1;

        // Client-supplied IDs and timestamps are ignored; the server owns both.
        course.setId(nextId);
        course.setCreatedAt(Instant.now());
        courses.add(course);
        writeCourses(courses);
        return course;
    }

    public synchronized Course updateCourse(long id, Course updatedCourse) {
        validateCourse(updatedCourse);
        List<Course> courses = readCourses();
        Course existingCourse = findCourse(courses, id)
                .orElseThrow(() -> new CourseNotFoundException(id));

        // Preserve the original identity and creation time when replacing details.
        updatedCourse.setId(existingCourse.getId());
        updatedCourse.setCreatedAt(existingCourse.getCreatedAt());
        courses.set(courses.indexOf(existingCourse), updatedCourse);
        writeCourses(courses);
        return updatedCourse;
    }

    public synchronized void deleteCourse(long id) {
        List<Course> courses = readCourses();
        Course course = findCourse(courses, id)
                .orElseThrow(() -> new CourseNotFoundException(id));
        courses.remove(course);
        writeCourses(courses);
    }

    private List<Course> readCourses() {
        try {
            List<Course> courses = objectMapper.readValue(
                    dataFile.toFile(), new TypeReference<>() { });
            return courses == null ? new ArrayList<>() : courses;
        } catch (IOException exception) {
            throw new CourseFileException("Could not read courses.json", exception);
        }
    }

    private void writeCourses(List<Course> courses) {
        Path temporaryFile = null;
        try {
            temporaryFile = Files.createTempFile(dataFile.getParent(), "courses-", ".tmp");
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(temporaryFile.toFile(), courses);
            try {
                Files.move(temporaryFile, dataFile, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                // Some file systems do not support atomic moves, so use a regular replacement.
                Files.move(temporaryFile, dataFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            throw new CourseFileException("Could not write courses.json", exception);
        } finally {
            if (temporaryFile != null) {
                try {
                    Files.deleteIfExists(temporaryFile);
                } catch (IOException ignored) {
                    // A leftover temporary file does not prevent the application from working.
                }
            }
        }
    }

    private Optional<Course> findCourse(List<Course> courses, long id) {
        return courses.stream()
                .filter(course -> course.getId() != null && course.getId() == id)
                .findFirst();
    }

    private void validateCourse(Course course) {
        if (course == null
                || course.getName() == null || course.getName().isBlank()
                || course.getDescription() == null || course.getDescription().isBlank()
                || course.getTargetDate() == null
                || course.getStatus() == null || course.getStatus().isBlank()) {
            throw new IllegalArgumentException("name, description, target_date, and status are required");
        }
        if (!ALLOWED_STATUSES.contains(course.getStatus())) {
            throw new IllegalArgumentException(
                    "status must be exactly one of: Not Started, In Progress, Completed");
        }
    }

    public static class CourseNotFoundException extends RuntimeException {
        public CourseNotFoundException(long id) {
            super("Course with id " + id + " was not found");
        }
    }

    public static class CourseFileException extends RuntimeException {
        public CourseFileException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}