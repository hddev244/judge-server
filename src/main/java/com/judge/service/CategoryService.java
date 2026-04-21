package com.judge.service;

import com.judge.api.dto.CategoryRequest;
import com.judge.api.dto.CategoryResponse;
import com.judge.domain.Category;
import com.judge.domain.Problem;
import com.judge.exception.JudgeException;
import com.judge.repository.CategoryRepository;
import com.judge.repository.ProblemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final ProblemRepository problemRepository;

    public CategoryService(CategoryRepository categoryRepository, ProblemRepository problemRepository) {
        this.categoryRepository = categoryRepository;
        this.problemRepository = problemRepository;
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> listAll() {
        return categoryRepository.findAllByOrderByNameAsc().stream()
                .map(c -> CategoryResponse.from(c, true))
                .toList();
    }

    @Transactional(readOnly = true)
    public CategoryResponse getById(Long id) {
        return CategoryResponse.from(getOrThrow(id), true);
    }

    @Transactional(readOnly = true)
    public CategoryResponse getBySlug(String slug) {
        Category category = categoryRepository.findBySlug(slug)
                .orElseThrow(() -> JudgeException.notFound("Category not found: " + slug));
        return CategoryResponse.from(category, true);
    }

    @Transactional
    public CategoryResponse create(CategoryRequest req) {
        if (categoryRepository.existsBySlug(req.getSlug()))
            throw JudgeException.badRequest("Category slug already exists: " + req.getSlug());
        if (categoryRepository.existsByName(req.getName()))
            throw JudgeException.badRequest("Category name already exists: " + req.getName());

        Category category = Category.builder()
                .name(req.getName())
                .slug(req.getSlug())
                .description(req.getDescription())
                .build();
        return CategoryResponse.from(categoryRepository.save(category), false);
    }

    @Transactional
    public CategoryResponse update(Long id, CategoryRequest req) {
        Category category = getOrThrow(id);
        if (!category.getSlug().equals(req.getSlug()) && categoryRepository.existsBySlug(req.getSlug()))
            throw JudgeException.badRequest("Category slug already exists: " + req.getSlug());
        if (!category.getName().equals(req.getName()) && categoryRepository.existsByName(req.getName()))
            throw JudgeException.badRequest("Category name already exists: " + req.getName());

        category.setName(req.getName());
        category.setSlug(req.getSlug());
        category.setDescription(req.getDescription());
        return CategoryResponse.from(categoryRepository.save(category), true);
    }

    @Transactional
    public void delete(Long id) {
        categoryRepository.delete(getOrThrow(id));
    }

    @Transactional
    public CategoryResponse addProblems(Long categoryId, List<Long> problemIds) {
        Category category = getOrThrow(categoryId);
        for (Long pid : problemIds) {
            Problem p = problemRepository.findById(pid)
                    .orElseThrow(() -> JudgeException.notFound("Problem not found: " + pid));
            category.getProblems().add(p);
            p.getCategories().add(category);
        }
        categoryRepository.save(category);
        return CategoryResponse.from(category, true);
    }

    @Transactional
    public CategoryResponse removeProblem(Long categoryId, Long problemId) {
        Category category = getOrThrow(categoryId);
        Problem p = problemRepository.findById(problemId)
                .orElseThrow(() -> JudgeException.notFound("Problem not found: " + problemId));
        category.getProblems().remove(p);
        p.getCategories().remove(category);
        categoryRepository.save(category);
        return CategoryResponse.from(category, true);
    }

    private Category getOrThrow(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> JudgeException.notFound("Category not found: " + id));
    }
}
