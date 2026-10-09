package com.technnext.hrms.performance.service;

import com.technnext.hrms.common.exception.BadRequestException;
import com.technnext.hrms.common.exception.ResourceNotFoundException;
import com.technnext.hrms.performance.dto.CycleRequest;
import com.technnext.hrms.performance.dto.GoalRequest;
import com.technnext.hrms.performance.dto.ManagerReviewRequest;
import com.technnext.hrms.performance.dto.ReviewResponse;
import com.technnext.hrms.performance.dto.SelfReviewRequest;
import com.technnext.hrms.performance.entity.PerformanceCycle;
import com.technnext.hrms.performance.entity.PerformanceGoal;
import com.technnext.hrms.performance.entity.PerformanceReview;
import com.technnext.hrms.performance.repository.PerformanceCycleRepository;
import com.technnext.hrms.performance.repository.PerformanceGoalRepository;
import com.technnext.hrms.performance.repository.PerformanceReviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Performance: HR opens a review cycle; employees (or their manager) set goals and track progress;
 * the employee submits a self review; the manager then completes it with a rating. The manager's
 * half stays hidden from the employee until the review is COMPLETED. A CLOSED cycle is read-only.
 */
@Service
@RequiredArgsConstructor
public class PerformanceService {

    private static final int MAX_GOALS_PER_CYCLE = 20;

    private final PerformanceCycleRepository cycleRepository;
    private final PerformanceGoalRepository goalRepository;
    private final PerformanceReviewRepository reviewRepository;

    // ── Cycles ──────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<PerformanceCycle> listCycles() {
        return cycleRepository.findAllByOrderByStartDateDesc();
    }

    @Transactional
    public PerformanceCycle createCycle(CycleRequest req) {
        String name = validateCycle(req, null);
        return cycleRepository.save(PerformanceCycle.builder()
                .name(name).startDate(req.startDate()).endDate(req.endDate()).status("OPEN").build());
    }

    @Transactional
    public PerformanceCycle updateCycle(Integer id, CycleRequest req) {
        PerformanceCycle c = findCycle(id);
        String name = validateCycle(req, c);
        c.setName(name);
        c.setStartDate(req.startDate());
        c.setEndDate(req.endDate());
        return cycleRepository.save(c);
    }

    @Transactional
    public PerformanceCycle setCycleStatus(Integer id, String status) {
        String s = status == null ? "" : status.trim().toUpperCase();
        if (!"OPEN".equals(s) && !"CLOSED".equals(s)) throw new BadRequestException("Status must be OPEN or CLOSED.");
        PerformanceCycle c = findCycle(id);
        c.setStatus(s);
        return cycleRepository.save(c);
    }

    // ── Goals ───────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<PerformanceGoal> goals(UUID employeeId, Integer cycleId) {
        findCycle(cycleId);
        return goalRepository.findByEmployeeIdAndCycleIdOrderByIdAsc(employeeId, cycleId);
    }

    @Transactional
    public PerformanceGoal createGoal(UUID employeeId, GoalRequest req) {
        if (req == null || req.cycleId() == null) throw new BadRequestException("Choose a review cycle.");
        PerformanceCycle cycle = requireOpen(findCycle(req.cycleId()));
        String title = validateGoalText(req);
        validateTarget(cycle, req.targetDate());
        int progress = validateProgress(req.progress() == null ? 0 : req.progress());
        if (goalRepository.countByEmployeeIdAndCycleId(employeeId, cycle.getId()) >= MAX_GOALS_PER_CYCLE) {
            throw new BadRequestException("At most " + MAX_GOALS_PER_CYCLE + " goals per cycle.");
        }
        return goalRepository.save(PerformanceGoal.builder()
                .cycleId(cycle.getId()).employeeId(employeeId).title(title)
                .description(clean(req.description())).targetDate(req.targetDate()).progress(progress).build());
    }

    @Transactional(readOnly = true)
    public PerformanceGoal getGoal(Integer id) {
        return goalRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Goal", id));
    }

    @Transactional
    public PerformanceGoal updateGoal(Integer id, GoalRequest req) {
        PerformanceGoal g = getGoal(id);
        PerformanceCycle cycle = requireOpen(findCycle(g.getCycleId()));
        String title = validateGoalText(req);
        validateTarget(cycle, req.targetDate());
        g.setTitle(title);
        g.setDescription(clean(req.description()));
        g.setTargetDate(req.targetDate());
        if (req.progress() != null) g.setProgress(validateProgress(req.progress()));
        return goalRepository.save(g);
    }

    @Transactional
    public void deleteGoal(Integer id) {
        PerformanceGoal g = getGoal(id);
        requireOpen(findCycle(g.getCycleId()));
        goalRepository.delete(g);
    }

    // ── Reviews ─────────────────────────────────────────────────────────────────

    /** The employee's own review; manager fields are hidden until COMPLETED. */
    @Transactional(readOnly = true)
    public ReviewResponse myReview(UUID employeeId, Integer cycleId) {
        findCycle(cycleId);
        return reviewRepository.findByCycleIdAndEmployeeId(cycleId, employeeId)
                .map(r -> ReviewResponse.from(r, true))
                .orElseGet(() -> ReviewResponse.notStarted(cycleId, employeeId));
    }

    @Transactional
    public ReviewResponse submitSelfReview(UUID employeeId, SelfReviewRequest req) {
        if (req == null || req.cycleId() == null) throw new BadRequestException("Choose a review cycle.");
        PerformanceCycle cycle = requireOpen(findCycle(req.cycleId()));
        int rating = validateRating(req.rating());
        String comments = clean(req.comments());
        if (comments == null) throw new BadRequestException("Please write a short self assessment.");
        if (comments.length() > 2000) throw new BadRequestException("Comments are too long (max 2000 characters).");
        if (reviewRepository.findByCycleIdAndEmployeeId(cycle.getId(), employeeId).isPresent()) {
            throw new BadRequestException("You have already submitted your self review for this cycle.");
        }
        PerformanceReview r = reviewRepository.save(PerformanceReview.builder()
                .cycleId(cycle.getId()).employeeId(employeeId).status("SELF_SUBMITTED")
                .selfRating(rating).selfComments(comments).selfSubmittedAt(LocalDateTime.now()).build());
        return ReviewResponse.from(r, true);
    }

    /** All submitted reviews of a cycle (the controller narrows them to the caller's scope). */
    @Transactional(readOnly = true)
    public List<ReviewResponse> cycleReviews(Integer cycleId) {
        findCycle(cycleId);
        return reviewRepository.findByCycleIdOrderBySelfSubmittedAtAsc(cycleId).stream()
                .map(r -> ReviewResponse.from(r, false)).toList();
    }

    @Transactional(readOnly = true)
    public ReviewResponse getReview(Integer id) {
        return ReviewResponse.from(findReview(id), false);
    }

    /** @param reviewerEmployeeId the manager's employee id, or null for a profile-less admin account */
    @Transactional
    public ReviewResponse completeReview(Integer id, UUID reviewerEmployeeId, ManagerReviewRequest req) {
        PerformanceReview r = findReview(id);
        requireOpen(findCycle(r.getCycleId()));
        if (!"SELF_SUBMITTED".equals(r.getStatus())) {
            throw new BadRequestException("This review has already been completed.");
        }
        if (reviewerEmployeeId != null && reviewerEmployeeId.equals(r.getEmployeeId())) {
            throw new BadRequestException("You can't review yourself.");
        }
        int rating = validateRating(req == null ? null : req.rating());
        String comments = clean(req.comments());
        if (comments == null) throw new BadRequestException("Please write feedback for the employee.");
        if (comments.length() > 2000) throw new BadRequestException("Comments are too long (max 2000 characters).");
        r.setManagerRating(rating);
        r.setManagerComments(comments);
        r.setReviewerId(reviewerEmployeeId);
        r.setCompletedAt(LocalDateTime.now());
        r.setStatus("COMPLETED");
        return ReviewResponse.from(reviewRepository.save(r), false);
    }

    // ── helpers ─────────────────────────────────────────────────────────────────

    private PerformanceCycle findCycle(Integer id) {
        return cycleRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Review cycle", id));
    }

    private PerformanceReview findReview(Integer id) {
        return reviewRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Review", id));
    }

    private PerformanceCycle requireOpen(PerformanceCycle c) {
        if (!"OPEN".equals(c.getStatus())) {
            throw new BadRequestException("Review cycle '" + c.getName() + "' is closed.");
        }
        return c;
    }

    private String validateCycle(CycleRequest req, PerformanceCycle existing) {
        String name = req == null ? null : clean(req.name());
        if (name == null) throw new BadRequestException("Cycle name is required.");
        if (name.length() > 150) throw new BadRequestException("Cycle name is too long (max 150).");
        if (req.startDate() == null || req.endDate() == null) throw new BadRequestException("Start and end dates are required.");
        if (req.endDate().isBefore(req.startDate())) throw new BadRequestException("End date can't be before the start date.");
        if (req.startDate().plusDays(731).isBefore(req.endDate())) throw new BadRequestException("A cycle can't be longer than two years.");
        boolean nameChanged = existing == null || !name.equalsIgnoreCase(existing.getName());
        if (nameChanged && cycleRepository.existsByNameIgnoreCase(name)) {
            throw new BadRequestException("A cycle named '" + name + "' already exists.");
        }
        return name;
    }

    private String validateGoalText(GoalRequest req) {
        String title = req == null ? null : clean(req.title());
        if (title == null) throw new BadRequestException("Goal title is required.");
        if (title.length() > 200) throw new BadRequestException("Goal title is too long (max 200).");
        if (req.description() != null && req.description().length() > 1000) {
            throw new BadRequestException("Goal description is too long (max 1000).");
        }
        return title;
    }

    private void validateTarget(PerformanceCycle cycle, LocalDate target) {
        if (target != null && (target.isBefore(cycle.getStartDate()) || target.isAfter(cycle.getEndDate()))) {
            throw new BadRequestException("Target date must fall within the cycle (" + cycle.getStartDate() + " to " + cycle.getEndDate() + ").");
        }
    }

    private int validateProgress(int p) {
        if (p < 0 || p > 100) throw new BadRequestException("Progress must be between 0 and 100.");
        return p;
    }

    private int validateRating(Integer rating) {
        if (rating == null || rating < 1 || rating > 5) throw new BadRequestException("Rating must be between 1 and 5.");
        return rating;
    }

    private static String clean(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}