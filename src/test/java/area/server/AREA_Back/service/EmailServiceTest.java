package area.server.AREA_Back.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import area.server.AREA_Back.service.Auth.EmailService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private MeterRegistry meterRegistry;

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private Counter emailSentCounter;

    @Mock
    private Counter emailFailedCounter;

    @Mock
    private Counter smtpEmailSentCounter;

    @Mock
    private Counter resendEmailSentCounter;

    @Mock
    private Counter resendFallbackCounter;

    @Mock
    private Timer smtpTimer;

    @Mock
    private Timer resendTimer;

    @InjectMocks
    private EmailService emailService;

    private MockedStatic<Counter> counterMockedStatic;
    private MockedStatic<Timer> timerMockedStatic;

    @BeforeEach
    void setUp() throws Exception {
        // Set up field values using reflection
        ReflectionTestUtils.setField(emailService, "smtpFromEmail", "noreply@test.com");
        ReflectionTestUtils.setField(emailService, "resendApiKey", "test-api-key");
        ReflectionTestUtils.setField(emailService, "resendFromEmail", "noreply@resend.com");
        ReflectionTestUtils.setField(emailService, "resendEnabled", true);
        // RestTemplate is now injected via constructor by @InjectMocks

        // Mock the Counter.Builder chain
        Counter.Builder emailSentBuilder = mock(Counter.Builder.class);
        Counter.Builder emailFailedBuilder = mock(Counter.Builder.class);
        Counter.Builder smtpEmailSentBuilder = mock(Counter.Builder.class);
        Counter.Builder resendEmailSentBuilder = mock(Counter.Builder.class);
        Counter.Builder resendFallbackBuilder = mock(Counter.Builder.class);
        Counter.Builder verificationEmailSentBuilder = mock(Counter.Builder.class);
        Counter.Builder passwordResetEmailSentBuilder = mock(Counter.Builder.class);
        
        // Set up static mocking for Counter
        counterMockedStatic = mockStatic(Counter.class);
        counterMockedStatic.when(() -> Counter.builder("email.sent")).thenReturn(emailSentBuilder);
        counterMockedStatic.when(() -> Counter.builder("email.failed")).thenReturn(emailFailedBuilder);
        counterMockedStatic.when(() -> Counter.builder("email.smtp.sent")).thenReturn(smtpEmailSentBuilder);
        counterMockedStatic.when(() -> Counter.builder("email.resend.sent")).thenReturn(resendEmailSentBuilder);
        counterMockedStatic.when(() -> Counter.builder("email.resend.fallback")).thenReturn(resendFallbackBuilder);
        counterMockedStatic.when(() -> Counter.builder("email.verification.sent")).thenReturn(verificationEmailSentBuilder);
        counterMockedStatic.when(() -> Counter.builder("email.password_reset.sent")).thenReturn(passwordResetEmailSentBuilder);
        
        when(emailSentBuilder.description(anyString())).thenReturn(emailSentBuilder);
        when(emailSentBuilder.register(meterRegistry)).thenReturn(emailSentCounter);
        
        when(emailFailedBuilder.description(anyString())).thenReturn(emailFailedBuilder);
        when(emailFailedBuilder.register(meterRegistry)).thenReturn(emailFailedCounter);
        
        when(smtpEmailSentBuilder.description(anyString())).thenReturn(smtpEmailSentBuilder);
        when(smtpEmailSentBuilder.register(meterRegistry)).thenReturn(smtpEmailSentCounter);
        
        when(resendEmailSentBuilder.description(anyString())).thenReturn(resendEmailSentBuilder);
        when(resendEmailSentBuilder.register(meterRegistry)).thenReturn(resendEmailSentCounter);
        
        when(resendFallbackBuilder.description(anyString())).thenReturn(resendFallbackBuilder);
        when(resendFallbackBuilder.register(meterRegistry)).thenReturn(resendFallbackCounter);
        
        when(verificationEmailSentBuilder.description(anyString())).thenReturn(verificationEmailSentBuilder);
        when(verificationEmailSentBuilder.register(meterRegistry)).thenReturn(mock(Counter.class));
        
        when(passwordResetEmailSentBuilder.description(anyString())).thenReturn(passwordResetEmailSentBuilder);
        when(passwordResetEmailSentBuilder.register(meterRegistry)).thenReturn(mock(Counter.class));

        // Mock the Timer.Builder chain
        Timer.Builder smtpTimerBuilder = mock(Timer.Builder.class);
        Timer.Builder resendTimerBuilder = mock(Timer.Builder.class);
        
        // Set up static mocking for Timer
        timerMockedStatic = mockStatic(Timer.class);
        timerMockedStatic.when(() -> Timer.builder("email.smtp.duration")).thenReturn(smtpTimerBuilder);
        timerMockedStatic.when(() -> Timer.builder("email.resend.duration")).thenReturn(resendTimerBuilder);
        
        when(smtpTimerBuilder.description(anyString())).thenReturn(smtpTimerBuilder);
        when(smtpTimerBuilder.register(meterRegistry)).thenReturn(smtpTimer);
        
        when(resendTimerBuilder.description(anyString())).thenReturn(resendTimerBuilder);
        when(resendTimerBuilder.register(meterRegistry)).thenReturn(resendTimer);

        // Mock Timer.recordCallable to actually execute the callable
        lenient().when(smtpTimer.recordCallable(any())).thenAnswer(invocation -> {
            java.util.concurrent.Callable<?> callable = invocation.getArgument(0);
            return callable.call();
        });
        
        lenient().when(resendTimer.recordCallable(any())).thenAnswer(invocation -> {
            java.util.concurrent.Callable<?> callable = invocation.getArgument(0);
            return callable.call();
        });

        // Initialize metrics
        emailService.initMetrics();
    }

    @AfterEach
    void tearDown() {
        if (counterMockedStatic != null) {
            counterMockedStatic.close();
        }
        if (timerMockedStatic != null) {
            timerMockedStatic.close();
        }
    }

    @Test
    void sendEmail_ShouldSendViaResend_WhenResendSucceeds() {
        // Given
        String to = "user@example.com";
        String subject = "Test Subject";
        String body = "Test Body";

        // Mock successful Resend response
        ResponseEntity<String> resendResponse = ResponseEntity.ok("{\"id\": \"test-id\"}");
        when(restTemplate.postForEntity(eq("https://api.resend.com/emails"), any(HttpEntity.class), eq(String.class)))
            .thenReturn(resendResponse);

        // When
        boolean result = emailService.sendEmail(to, subject, body);

        // Then
        assertTrue(result);
        verify(restTemplate).postForEntity(eq("https://api.resend.com/emails"), any(HttpEntity.class), eq(String.class));
        verify(emailSentCounter).increment();
        verify(resendEmailSentCounter).increment();
        verify(smtpEmailSentCounter, never()).increment();
        verify(resendFallbackCounter, never()).increment();
    }

    @Test
    void sendEmail_ShouldFallbackToSmtp_WhenResendFails() {
        // Given
        String to = "user@example.com";
        String subject = "Test Subject";
        String body = "Test Body";

        // Mock failed Resend response
        when(restTemplate.postForEntity(eq("https://api.resend.com/emails"), any(HttpEntity.class), eq(String.class)))
            .thenThrow(new RuntimeException("Resend failed"));

        // Mock successful SMTP
        doNothing().when(mailSender).send(any(SimpleMailMessage.class));

        // When
        boolean result = emailService.sendEmail(to, subject, body);

        // Then
        assertTrue(result);
        verify(restTemplate).postForEntity(eq("https://api.resend.com/emails"), any(HttpEntity.class), eq(String.class));
        verify(mailSender).send(any(SimpleMailMessage.class));
        verify(emailSentCounter).increment();
        verify(emailFailedCounter, never()).increment();
        verify(smtpEmailSentCounter).increment();
        verify(resendFallbackCounter).increment();
    }

    @Test
    void sendEmail_ShouldReturnFalse_WhenBothSmtpAndResendFail() {
        // Given
        String to = "user@example.com";
        String subject = "Test Subject";
        String body = "Test Body";

        // Mock failed Resend response
        when(restTemplate.postForEntity(eq("https://api.resend.com/emails"), any(HttpEntity.class), eq(String.class)))
            .thenThrow(new RuntimeException("Resend failed"));

        // Mock failed SMTP
        doThrow(new MailException("SMTP failed") {}).when(mailSender).send(any(SimpleMailMessage.class));

        // When
        boolean result = emailService.sendEmail(to, subject, body);

        // Then
        assertFalse(result);
        verify(restTemplate).postForEntity(eq("https://api.resend.com/emails"), any(HttpEntity.class), eq(String.class));
        verify(mailSender).send(any(SimpleMailMessage.class));
        verify(emailFailedCounter).increment();
        verify(resendFallbackCounter).increment();
    }

    @Test
    void sendEmail_ShouldReturnFalse_WhenResendDisabled() {
        // Given
        ReflectionTestUtils.setField(emailService, "resendEnabled", false);
        String to = "user@example.com";
        String subject = "Test Subject";
        String body = "Test Body";

        doThrow(new MailException("SMTP failed") {}).when(mailSender).send(any(SimpleMailMessage.class));

        // When
        boolean result = emailService.sendEmail(to, subject, body);

        // Then
        assertFalse(result);
        verify(mailSender).send(any(SimpleMailMessage.class));
        verify(restTemplate, never()).postForEntity(anyString(), any(HttpEntity.class), eq(String.class));
        verify(emailFailedCounter).increment();
        verify(resendFallbackCounter, never()).increment();
    }

    @Test
    void sendVerificationEmail_ShouldCallSendEmailWithCorrectContent() {
        // Given
        String to = "user@example.com";
        String subject = "Verify Your Account";
        String verificationUrl = "http://localhost:3000/verify?token=abc123";

        doNothing().when(mailSender).send(any(SimpleMailMessage.class));

        // When
        boolean result = emailService.sendVerificationEmail(to, subject, verificationUrl);

        // Then
        assertTrue(result);
        verify(mailSender).send(any(SimpleMailMessage.class));
    }

    @Test
    void sendPasswordResetEmail_ShouldCallSendEmailWithCorrectContent() {
        // Given
        String to = "user@example.com";
        String subject = "Reset Your Password";
        String resetUrl = "http://localhost:3000/reset?token=xyz789";

        doNothing().when(mailSender).send(any(SimpleMailMessage.class));

        // When
        boolean result = emailService.sendPasswordResetEmail(to, subject, resetUrl);

        // Then
        assertTrue(result);
        verify(mailSender).send(any(SimpleMailMessage.class));
    }

    @Test
    void sendEmailViaResend_ShouldReturnTrue_WhenApiCallSucceeds() {
        // Given
        String to = "user@example.com";
        String subject = "Test Subject";
        String body = "Test Body";

        ResponseEntity<String> successResponse = ResponseEntity.ok("{\"id\": \"test-id\"}");
        when(restTemplate.postForEntity(eq("https://api.resend.com/emails"), any(HttpEntity.class), eq(String.class)))
            .thenReturn(successResponse);

        // When
        boolean result = ReflectionTestUtils.invokeMethod(emailService, "sendEmailViaResend", to, subject, body);

        // Then
        assertTrue(result);
        verify(restTemplate).postForEntity(eq("https://api.resend.com/emails"), any(HttpEntity.class), eq(String.class));
        verify(resendEmailSentCounter).increment();
    }

    @Test
    void sendEmailViaResend_ShouldReturnFalse_WhenApiCallFails() {
        // Given
        String to = "user@example.com";
        String subject = "Test Subject";
        String body = "Test Body";

        when(restTemplate.postForEntity(eq("https://api.resend.com/emails"), any(HttpEntity.class), eq(String.class)))
            .thenThrow(new RuntimeException("API Error"));

        // When
        boolean result = ReflectionTestUtils.invokeMethod(emailService, "sendEmailViaResend", to, subject, body);

        // Then
        assertFalse(result);
        verify(restTemplate).postForEntity(eq("https://api.resend.com/emails"), any(HttpEntity.class), eq(String.class));
        verify(resendEmailSentCounter, never()).increment();
    }
}