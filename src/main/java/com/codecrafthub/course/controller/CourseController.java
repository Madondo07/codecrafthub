package com.codecrafthub.course.controller;

import com.codecrafthub.course.model.Course;
import com.codecrafthub.course.service.CourseService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

/** Maps HTTP requests to course operations and turns common failures into useful responses. */
@RestController
@RequestMapping("/api/courses")
public class CourseController {

    private final CourseService courseService;

    public CourseController(CourseService courseService) {
        this.courseService = courseService;
    }

    @PostMapping
    public ResponseEntity<Course> addCourse(@Valid @RequestBody Course course) {
        Course createdCourse = courseService.addCourse(course);
        return ResponseEntity.created(URI.create("/api/courses/" + createdCourse.getId()))
                .body(createdCourse);
    }

    @GetMapping
    public List<Course> getAllCourses() {
        return courseService.getAllCourses();
    }

    @GetMapping("/stats")
    public Map<String, Object> getCourseStats() {
        List<Course> courses = courseService.getAllCourses();
        Map<String, Long> coursesByStatus = new LinkedHashMap<>();
        List.of("Not Started", "In Progress", "Completed")
                .forEach(status -> coursesByStatus.put(status, 0L));
        courses.forEach(course -> coursesByStatus.computeIfPresent(
                course.getStatus(), (status, count) -> count + 1));

        return Map.of("totalCourses", courses.size(), "coursesByStatus", coursesByStatus);
    }

    @GetMapping("/{id}")
    public Course getCourseById(@PathVariable long id) {
        return courseService.getCourseById(id);
    }

    @PutMapping("/{id}")
    public Course updateCourse(@PathVariable long id, @Valid @RequestBody Course course) {
        return courseService.updateCourse(id, course);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCourse(@PathVariable long id) {
        courseService.deleteCourse(id);
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidationError(
            MethodArgumentNotValidException exception) {
        FieldError fieldError = exception.getBindingResult().getFieldErrors().get(0);
        return error(HttpStatus.BAD_REQUEST, fieldError.getDefaultMessage());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> handleUnreadableJson(
            HttpMessageNotReadableException exception) {
        return error(HttpStatus.BAD_REQUEST,
                "Request JSON is invalid; target_date must use YYYY-MM-DD format");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleInvalidCourse(
            IllegalArgumentException exception) {
        return error(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(CourseService.CourseNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleCourseNotFound(
            CourseService.CourseNotFoundException exception) {
        return error(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(CourseService.CourseFileException.class)
    public ResponseEntity<Map<String, String>> handleFileError(
            CourseService.CourseFileException exception) {
        return error(HttpStatus.INTERNAL_SERVER_ERROR, exception.getMessage());
    }

    private ResponseEntity<Map<String, String>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of("error", message));
    }
}