package server.poptato.todo.application;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import server.poptato.external.firebase.service.FCMService;
import server.poptato.todo.domain.entity.Todo;
import server.poptato.todo.domain.repository.TodoRepository;
import server.poptato.todo.domain.value.TodayStatus;
import server.poptato.todo.domain.value.Type;
import server.poptato.user.domain.entity.Mobile;
import server.poptato.user.domain.entity.User;
import server.poptato.user.domain.repository.MobileRepository;
import server.poptato.user.domain.repository.UserRepository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;


@Service
@RequiredArgsConstructor
public class TodoScheduler {
    private final TodoRepository todoRepository;
    private final UserRepository userRepository;
    private final MobileRepository mobileRepository;
    private final FCMService fcmService;

    @Scheduled(cron = "0 0 0 * * *")
    @Transactional
    public void updateTodoType() {
        List<Long> updatedTodoIds = new ArrayList<>();
        updateTodo(updatedTodoIds);
        sendDeadlineNotifications();
    }
    @Async
    public void updateTodo(List<Long> updatedTodoIds) {
        Map<Long, List<Todo>> userIdAndTodaysMap = updateTodays(updatedTodoIds);
        List<Todo> yesterdayTodos = updateYesterdays(updatedTodoIds);
        save(userIdAndTodaysMap, yesterdayTodos);
    }

    private Map<Long, List<Todo>> updateTodays(List<Long> updatedTodoIds) {
        Map<Long, List<Todo>> userIdAndTodaysMap = todoRepository.findByType(Type.TODAY)
                .stream()
                .collect(Collectors.groupingBy(Todo::getUserId));

        userIdAndTodaysMap.forEach((userId, todos) -> {
            Integer minBacklogOrder = todoRepository.findMinBacklogOrderByUserIdOrZero(userId);
            int startingOrder = minBacklogOrder - 1;

            for (Todo todo : todos) {
                if (todo.getTodayStatus() == TodayStatus.COMPLETED && todo.isRepeat()) {
                    todo.setType(Type.BACKLOG);
                    todo.setTodayStatus(null);
                    todo.setTodayOrder(null);
                    todo.setBacklogOrder(startingOrder--);
                    updatedTodoIds.add(todo.getId());
                    continue;
                }

                if (todo.getTodayStatus() == TodayStatus.INCOMPLETE) {
                    todo.setType(Type.YESTERDAY);
                    todo.setTodayOrder(null);
                    updatedTodoIds.add(todo.getId());
                }
            }
        });
        return userIdAndTodaysMap;
    }

    private List<Todo> updateYesterdays(List<Long> updatedTodoIds) {
        return todoRepository.findByType(Type.YESTERDAY)
                .stream()
                .filter(todo -> !updatedTodoIds.contains(todo.getId()))
                .peek(todo -> {
                    if (todo.getTodayStatus() == TodayStatus.INCOMPLETE) {
                        todo.setType(Type.BACKLOG);
                        todo.setTodayStatus(null);
                    } else if (todo.getTodayStatus() == TodayStatus.COMPLETED && todo.isRepeat()) {
                        todo.setType(Type.BACKLOG);
                        todo.setTodayStatus(null);
                    }
                })
                .collect(Collectors.toList());
    }

    private void save(Map<Long, List<Todo>> userIdAndTodaysMap, List<Todo> yesterdayTodos) {
        for (Todo todo : userIdAndTodaysMap.values().stream().flatMap(List::stream).collect(Collectors.toList())) {
            todoRepository.save(todo);
        }
        for (Todo todo : yesterdayTodos) {
            todoRepository.save(todo);
        }
    }
    @Async
    public void sendDeadlineNotifications() {
        List<User> users = userRepository.findAll();

        for (User user : users) {
            if (Boolean.TRUE.equals(user.getIsPushAlarm())) {
                List<Todo> todosDueToday = todoRepository.findTodosDueToday(user.getId(), LocalDate.now());
                sendFcmMessage(user, todosDueToday);

            }
        }
    }

    private void sendFcmMessage(User user, List<Todo> todosDueToday) {
        if (!todosDueToday.isEmpty()) {
            Optional<Mobile> mobile = mobileRepository.findByUserId(user.getId());
            if (mobile.isPresent()) {
                for (Todo todo : todosDueToday) {
                    String todoContent = formatTodoContent(todo);
                    fcmService.sendPushNotification(
                            mobile.get().getClientId(),
                            "오늘 마감 예정인 할 일",
                            todoContent
                    );
                }
            }
        }
    }

    private String formatTodoContent(Todo todo) {
        return ": " + todo.getContent();
    }
}

