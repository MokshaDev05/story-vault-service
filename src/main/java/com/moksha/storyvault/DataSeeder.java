package com.moksha.storyvault;

import com.moksha.storyvault.model.User;
import com.moksha.storyvault.repository.StoryRepository;
import com.moksha.storyvault.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataSeeder implements ApplicationRunner {

    private final UserRepository userRepository;
    private final StoryRepository storyRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepository.count() == 0) {
            User demo = userRepository.save(User.builder()
                    .username("demo")
                    .password(passwordEncoder.encode("demo123"))
                    .build());

            var orphaned = storyRepository.findByUserIsNull();
            orphaned.forEach(s -> s.setUser(demo));
            storyRepository.saveAll(orphaned);

            log.info("Created demo user and assigned {} orphaned stories", orphaned.size());
        }
    }
}
