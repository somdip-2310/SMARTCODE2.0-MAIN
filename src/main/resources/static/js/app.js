// SmartCode 2.0 - Common JavaScript Functions

// Page transition with loading screen
function showLoadingScreen(message = 'Loading...') {
    const loadingHTML = `
        <div class="loading-screen" id="pageLoadingScreen">
            <div class="loading-content">
                <div class="loading-spinner"></div>
                <p class="mt-3">${message}</p>
            </div>
        </div>
    `;
    
    document.body.insertAdjacentHTML('beforeend', loadingHTML);
    
    setTimeout(() => {
        const loadingScreen = document.getElementById('pageLoadingScreen');
        if (loadingScreen) {
            loadingScreen.style.opacity = '1';
        }
    }, 10);
}

// Hide loading screen
function hideLoadingScreen() {
    const loadingScreen = document.getElementById('pageLoadingScreen');
    if (loadingScreen) {
        loadingScreen.style.opacity = '0';
        setTimeout(() => {
            loadingScreen.remove();
        }, 500);
    }
}

// Smooth page transitions
document.addEventListener('DOMContentLoaded', function() {
    // Add loading screen to all navigation links
    const navLinks = document.querySelectorAll('a[href^="/"], a[href^="http"]');
    navLinks.forEach(link => {
        if (!link.hasAttribute('target') || link.getAttribute('target') !== '_blank') {
            link.addEventListener('click', function(e) {
                if (!e.ctrlKey && !e.metaKey) {
                    e.preventDefault();
                    showLoadingScreen('Navigating...');
                    setTimeout(() => {
                        window.location.href = link.href;
                    }, 300);
                }
            });
        }
    });
    
    // Animate elements on scroll
    const observerOptions = {
        threshold: 0.1,
        rootMargin: '0px 0px -50px 0px'
    };
    
    const observer = new IntersectionObserver(function(entries) {
        entries.forEach(entry => {
            if (entry.isIntersecting) {
                entry.target.classList.add('fade-in');
                observer.unobserve(entry.target);
            }
        });
    }, observerOptions);
    
    // Observe all glass containers
    document.querySelectorAll('.glass-container, .feature-card, .issue-card').forEach(el => {
        el.style.opacity = '0';
        el.style.transform = 'translateY(20px)';
        observer.observe(el);
    });
});

// Copy to clipboard with animation
function copyToClipboard(text, button) {
    navigator.clipboard.writeText(text).then(() => {
        const originalContent = button.innerHTML;
        button.innerHTML = '<i class="fas fa-check me-1"></i>Copied!';
        button.classList.add('btn-success');
        
        setTimeout(() => {
            button.innerHTML = originalContent;
            button.classList.remove('btn-success');
        }, 2000);
    }).catch(err => {
        console.error('Failed to copy:', err);
        alert('Failed to copy to clipboard');
    });
}

// Format file sizes
function formatFileSize(bytes) {
    if (bytes === 0) return '0 Bytes';
    const k = 1024;
    const sizes = ['Bytes', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
}

// Time ago formatter
function timeAgo(date) {
    const seconds = Math.floor((new Date() - date) / 1000);
    
    let interval = seconds / 31536000;
    if (interval > 1) return Math.floor(interval) + " years ago";
    
    interval = seconds / 2592000;
    if (interval > 1) return Math.floor(interval) + " months ago";
    
    interval = seconds / 86400;
    if (interval > 1) return Math.floor(interval) + " days ago";
    
    interval = seconds / 3600;
    if (interval > 1) return Math.floor(interval) + " hours ago";
    
    interval = seconds / 60;
    if (interval > 1) return Math.floor(interval) + " minutes ago";
    
    return Math.floor(seconds) + " seconds ago";
}

// Session expiry warning
function checkSessionExpiry(expiryTime) {
    const checkInterval = setInterval(() => {
        const now = new Date().getTime();
        const expiry = new Date(expiryTime).getTime();
        const timeLeft = expiry - now;
        
        // Warn 5 minutes before expiry
        if (timeLeft < 5 * 60 * 1000 && timeLeft > 0) {
            if (!document.getElementById('sessionWarning')) {
                const warningHTML = `
                    <div id="sessionWarning" class="alert alert-warning position-fixed bottom-0 end-0 m-3" style="z-index: 9999;">
                        <i class="fas fa-exclamation-triangle me-2"></i>
                        Your session will expire in <span id="sessionCountdown"></span>
                        <button class="btn btn-sm btn-warning ms-2" onclick="location.reload()">
                            <i class="fas fa-sync"></i> Refresh
                        </button>
                    </div>
                `;
                document.body.insertAdjacentHTML('beforeend', warningHTML);
            }
            
            const minutes = Math.floor(timeLeft / 60000);
            const seconds = Math.floor((timeLeft % 60000) / 1000);
            document.getElementById('sessionCountdown').textContent = `${minutes}:${seconds.toString().padStart(2, '0')}`;
        }
        
        // Session expired
        if (timeLeft <= 0) {
            clearInterval(checkInterval);
            showLoadingScreen('Session expired. Redirecting...');
            setTimeout(() => {
                window.location.href = '/';
            }, 2000);
        }
    }, 1000);
}

// Progress bar animation
function animateProgress(elementId, targetPercentage, duration = 1000) {
    const element = document.getElementById(elementId);
    if (!element) return;
    
    const start = 0;
    const increment = targetPercentage / (duration / 10);
    let current = start;
    
    const timer = setInterval(() => {
        current += increment;
        if (current >= targetPercentage) {
            current = targetPercentage;
            clearInterval(timer);
        }
        
        element.style.width = current + '%';
        element.setAttribute('aria-valuenow', current);
        
        // Update text if it exists
        const text = element.querySelector('.progress-text');
        if (text) {
            text.textContent = Math.round(current) + '%';
        }
    }, 10);
}

// Notification system
function showNotification(message, type = 'info', duration = 5000) {
    const icons = {
        success: 'fas fa-check-circle',
        error: 'fas fa-exclamation-circle',
        warning: 'fas fa-exclamation-triangle',
        info: 'fas fa-info-circle'
    };
    
    const colors = {
        success: '#10b981',
        error: '#ef4444',
        warning: '#f59e0b',
        info: '#3b82f6'
    };
    
    const notificationHTML = `
        <div class="notification" style="
            position: fixed;
            top: 20px;
            right: 20px;
            background: rgba(255, 255, 255, 0.1);
            backdrop-filter: blur(10px);
            border: 1px solid rgba(255, 255, 255, 0.2);
            border-radius: 12px;
            padding: 16px 20px;
            display: flex;
            align-items: center;
            gap: 12px;
            min-width: 300px;
            z-index: 10000;
            transform: translateX(400px);
            transition: transform 0.3s ease-out;
        ">
            <i class="${icons[type]}" style="color: ${colors[type]}; font-size: 1.5rem;"></i>
            <span style="color: white;">${message}</span>
        </div>
    `;
    
    document.body.insertAdjacentHTML('beforeend', notificationHTML);
    
    const notification = document.querySelector('.notification:last-child');
    setTimeout(() => {
        notification.style.transform = 'translateX(0)';
    }, 10);
    
    setTimeout(() => {
        notification.style.transform = 'translateX(400px)';
        setTimeout(() => {
            notification.remove();
        }, 300);
    }, duration);
}

// Export functions for use in other scripts
window.SmartCode = {
    showLoadingScreen,
    hideLoadingScreen,
    copyToClipboard,
    formatFileSize,
    timeAgo,
    checkSessionExpiry,
    animateProgress,
    showNotification
};