package com.studyflow.studyflow.service;

import com.studyflow.studyflow.dto.SubjectDto;
import com.studyflow.studyflow.entity.Subject;
import com.studyflow.studyflow.repository.SubjectRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Implementation of SubjectService.
 *
 * Contains the business logic for Subject CRUD operations.
 */
@Service
public class SubjectServiceImpl implements SubjectService {

    private final SubjectRepository subjectRepository;

    /**
     * Constructor Injection.
     * Spring automatically injects the SubjectRepository bean.
     */
    public SubjectServiceImpl(SubjectRepository subjectRepository) {
        this.subjectRepository = subjectRepository;
    }

    /**
     * Save a new subject.
     */
    @Override
    public SubjectDto saveSubject(SubjectDto subjectDto) {

        // Convert DTO to Entity
        Subject subject = new Subject();
        subject.setName(subjectDto.name());
        subject.setDescription(subjectDto.description());
        subject.setColor(subjectDto.color());

        // Save Entity
        Subject savedSubject = subjectRepository.save(subject);

        // Convert Entity back to DTO
        return new SubjectDto(
                savedSubject.getId(),
                savedSubject.getName(),
                savedSubject.getDescription(),
                savedSubject.getColor()
        );
    }

    /**
     * Retrieve all subjects.
     */
    @Override
    public List<SubjectDto> getAllSubjects() {

        return subjectRepository.findAll()
                .stream()
                .map(subject -> new SubjectDto(
                        subject.getId(),
                        subject.getName(),
                        subject.getDescription(),
                        subject.getColor()
                ))
                .collect(Collectors.toList());
    }

    /**
     * Retrieve a subject by ID.
     */
    @Override
    public SubjectDto getSubjectById(Long id) {

        Subject subject = subjectRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Subject not found with id: " + id));

        return new SubjectDto(
                subject.getId(),
                subject.getName(),
                subject.getDescription(),
                subject.getColor()
        );
    }

    /**
     * Update an existing subject.
     */
    @Override
    public SubjectDto updateSubject(Long id, SubjectDto subjectDto) {

        Subject subject = subjectRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Subject not found with id: " + id));

        subject.setName(subjectDto.name());
        subject.setDescription(subjectDto.description());
        subject.setColor(subjectDto.color());

        Subject updatedSubject = subjectRepository.save(subject);

        return new SubjectDto(
                updatedSubject.getId(),
                updatedSubject.getName(),
                updatedSubject.getDescription(),
                updatedSubject.getColor()
        );
    }

    /**
     * Delete a subject by ID.
     */
    @Override
    public void deleteSubject(Long id) {

        if (!subjectRepository.existsById(id)) {
            throw new RuntimeException("Subject not found with id: " + id);
        }

        subjectRepository.deleteById(id);
    }
}