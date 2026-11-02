package com.codecrafthub.course.controller;

import com.codecrafthub.course.model.Course;
import com.codecrafthub.course.service.CourseService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CourseController.class)
class CourseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CourseService courseService;

    @Test
    void getCourseStatsReturnsTotalAndCountsByStatus() throws Exception {
        when(courseService.getAllCourses()).thenReturn(List.of(
                courseWithStatus("Not Started"),
                courseWithStatus("Not Started"),
                courseWithStatus("In Progress")));

        mockMvc.perform(get("/api/courses/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCourses").value(3))
                .andExpect(jsonPath("$.coursesByStatus['Not Started']").value(2))
                .andExpect(jsonPath("$.coursesByStatus['In Progress']").value(1))
                .andExpect(jsonPath("$.coursesByStatus.Completed").value(0));
    }

    private Course courseWithStatus(String status) {
        Course course = new Course();
        course.setStatus(status);
        return course;
    }
}