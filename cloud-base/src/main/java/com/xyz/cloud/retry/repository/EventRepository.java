package com.xyz.cloud.retry.repository;

import com.xyz.cloud.retry.RetryableEvent;
import com.xyz.utils.JsonUtils;
import com.xyz.utils.ValidationUtils;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.context.ApplicationContext;

import javax.validation.constraints.NotNull;
import java.lang.reflect.Method;
import java.util.List;

/**
 * Append-only repository for storing Spring Event
 */
public interface EventRepository {
    EventRepository newRepository(@NotNull Class<? extends RetryableEvent> eventClass);

    void add(@NotNull EventRepository.EventItem<? extends RetryableEvent> item);

    List<EventItem<RetryableEvent>> list();

    void remove(@NotNull EventRepository.EventItem<? extends RetryableEvent> item);

    void update(@NotNull EventRepository.EventItem<? extends RetryableEvent> item);

    long size();

    Class<? extends RetryableEvent> getEventClass();

    @Data
    @Slf4j
    class EventItem<T extends RetryableEvent> {
        private String listenerClassName;
        private String actionMethodName;
        private T event;
        private int maxAttempts;

        public static <T extends RetryableEvent> EventItem<T> create(String listenerClassName, String actionMethodName, T event, int maxAttempts) {
            EventItem<T> item = new EventItem<>();
            item.listenerClassName = listenerClassName;
            item.actionMethodName = actionMethodName;
            item.event = event;
            item.maxAttempts = maxAttempts;
            return item;
        }

        public void redo(ApplicationContext ctx) {
            try {
                log.debug("Retrying event {}", JsonUtils.beanToJson(event));
                Object listener = ctx.getBean(Class.forName(listenerClassName));
                Method method = BeanUtils.findDeclaredMethod(listener.getClass(), actionMethodName, event.getClass());
                ValidationUtils.notNull(method, String.format("Method %s not found in class %s", actionMethodName, listenerClassName));
                method.invoke(listener, event);
            } catch (Throwable e) {
                log.warn("Failed to handle event {}", JsonUtils.beanToJson(event), e);
            }
        }

        public String getId() {
            return String.format("%s_%s_%s", listenerClassName, actionMethodName, event.getTraceId());
        }

        public static final EventItem<RetryableEvent> fromJson(String json, Class<? extends RetryableEvent> eventClass) {
            EventItem<RetryableEvent> eventItem = JsonUtils.jsonToBean(json, EventItem.class, eventClass);
            return eventItem;
        }
    }

}