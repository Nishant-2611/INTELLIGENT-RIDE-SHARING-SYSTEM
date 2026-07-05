document.addEventListener('DOMContentLoaded', () => {
    // API base URL
    const API_URL = '';

    // Active state and tabs
    let currentTab = 'dashboard';
    let simulationData = null;
    let mapAnimationId = null;
    let isAnimationPaused = false;
    let animationSpeed = 0.02;

    // Elements
    const menuItems = document.querySelectorAll('.menu-item');
    const tabContents = document.querySelectorAll('.tab-content');
    const tabTitleText = document.getElementById('tab-title-text');
    const btnRunSim = document.getElementById('btn-run-simulation');
    const btnRefresh = document.getElementById('btn-refresh-stats');
    const simBanner = document.getElementById('sim-status-banner');
    const logConsole = document.getElementById('log-console');
    const btnToggleAnim = document.getElementById('btn-toggle-animation');

    // Canvas properties
    const previewCanvas = document.getElementById('map-preview-canvas');
    const fullCanvas = document.getElementById('map-full-canvas');
    const previewCtx = previewCanvas.getContext('2d');
    const fullCtx = fullCanvas.getContext('2d');

    // Tab switcher
    menuItems.forEach(item => {
        item.addEventListener('click', (e) => {
            e.preventDefault();
            const targetTab = item.getAttribute('data-tab');
            switchTab(targetTab);
        });
    });

    function switchTab(tabId) {
        currentTab = tabId;
        
        // Update sidebar menu active classes
        menuItems.forEach(item => {
            if (item.getAttribute('data-tab') === tabId) {
                item.classList.add('active');
            } else {
                item.classList.remove('active');
            }
        });

        // Toggle active tab content visibility
        tabContents.forEach(content => {
            if (content.id === `tab-${tabId}`) {
                content.classList.add('active');
            } else {
                content.classList.remove('active');
            }
        });

        // Set header title
        const titles = {
            dashboard: 'Dashboard Overview',
            map: 'Live Simulation Map',
            conflicts: 'System Integrity & Conflict Registry',
            rides: 'Completed Ride Ledger',
            schema: 'Relational Database Schema & OOP Design',
            users: 'Riders, Drivers & Vehicles Directory'
        };
        tabTitleText.textContent = titles[tabId] || 'Ridesharing System';

        // Trigger map animation resize if switching to map
        if (tabId === 'map' || tabId === 'dashboard') {
            startMapVisualizer();
        }
    }

    // Run Simulation
    btnRunSim.addEventListener('click', triggerSimulationRun);
    btnRefresh.addEventListener('click', loadDashboardStats);

    async function triggerSimulationRun() {
        try {
            // UI States
            btnRunSim.disabled = true;
            simBanner.classList.remove('hidden');
            logConsole.innerHTML = '<div class="log-line">Starting matching engine...</div>';

            const response = await fetch(`${API_URL}/api/simulation/run`, {
                method: 'POST'
            });

            if (!response.ok) {
                throw new Error('Simulation failed on backend server.');
            }

            simulationData = await response.json();
            
            // Render logs
            renderLogs(simulationData.simulationLogs);
            
            // Load dashboard stats
            loadDashboardStats();
            
            // Stop banner
            simBanner.classList.add('hidden');
            btnRunSim.disabled = false;

            // Switch to dashboard tab to show updates
            switchTab('dashboard');

        } catch (error) {
            console.error(error);
            logConsole.innerHTML = `<div class="log-line text-red">Error during simulation: ${error.message}</div>`;
            simBanner.classList.add('hidden');
            btnRunSim.disabled = false;
        }
    }

    function renderLogs(logs) {
        logConsole.innerHTML = '';
        if (!logs || logs.length === 0) return;

        logs.forEach(line => {
            const div = document.createElement('div');
            div.className = 'log-line';
            if (line.startsWith('---')) {
                div.className += ' stage-header';
            }
            if (line.includes('conflict') || line.includes('Scarcity') || line.includes('Exceeded')) {
                div.style.color = '#f1c40f'; // highlight warnings
            }
            if (line.includes('Double Request') || line.includes('failure')) {
                div.style.color = '#ff4b2b'; // red warnings
            }
            div.textContent = line;
            logConsole.appendChild(div);
        });

        // Auto scroll to bottom
        logConsole.scrollTop = logConsole.scrollHeight;
        document.getElementById('log-count').textContent = `${logs.length} events logged`;
    }

    async function loadDashboardStats() {
        try {
            const response = await fetch(`${API_URL}/api/dashboard/stats`);
            if (!response.ok) throw new Error('Failed to fetch stats');

            const stats = await response.json();
            
            // Populate stats text
            document.getElementById('stat-total-requests').textContent = stats.totalRequests;
            document.getElementById('stat-completed-rides').textContent = stats.completedRides;
            document.getElementById('stat-total-conflicts').textContent = stats.totalConflicts;
            document.getElementById('stat-drivers-vehicles').textContent = `${stats.driversCount} / ${stats.vehiclesCount}`;

            // Calculate percentage conflicts
            const totalConflicts = stats.totalConflicts || 1; // avoid divide by zero
            const pScarcity = Math.round((stats.scarcityConflict / totalConflicts) * 100) || 0;
            const pCapacity = Math.round((stats.capacityConflict / totalConflicts) * 100) || 0;
            const pDouble = Math.round((stats.doubleRequestsConflict / totalConflicts) * 100) || 0;

            document.getElementById('pct-scarcity').textContent = `${pScarcity}% (${stats.scarcityConflict})`;
            document.getElementById('pct-capacity').textContent = `${pCapacity}% (${stats.capacityConflict})`;
            document.getElementById('pct-double').textContent = `${pDouble}% (${stats.doubleRequestsConflict})`;

            document.getElementById('bar-scarcity').style.width = `${pScarcity}%`;
            document.getElementById('bar-capacity').style.width = `${pCapacity}%`;
            document.getElementById('bar-double').style.width = `${pDouble}%`;

            // Load Tables
            loadConflictsTable();
            loadRidesTable();
            loadUsersTable();

            // Refresh Map
            startMapVisualizer();

        } catch (error) {
            console.error('Stats load error', error);
        }
    }

    async function loadConflictsTable() {
        try {
            const response = await fetch(`${API_URL}/api/conflicts`);
            const conflicts = await response.json();
            
            // Populate recent conflicts on dashboard
            const recentTbody = document.getElementById('tbl-recent-conflicts');
            recentTbody.innerHTML = '';
            
            const recentConflicts = conflicts.slice().reverse().slice(0, 5);
            if (recentConflicts.length === 0) {
                recentTbody.innerHTML = '<tr><td colspan="3" class="text-center text-muted">No conflicts recorded yet.</td></tr>';
            } else {
                recentConflicts.forEach(c => {
                    const row = document.createElement('tr');
                    const badgeClass = c.conflictType === 'DRIVER_SCARCITY' ? 'badge-conflict' : (c.conflictType === 'CAPACITY_EXCEEDED' ? 'badge-warning' : 'badge-purple');
                    row.innerHTML = `
                        <td><span class="badge ${badgeClass}">${c.conflictType}</span></td>
                        <td>Request #${c.request.id}</td>
                        <td>
                            <div class="text-white">${c.details}</div>
                            <small class="text-green">Action: ${c.resolutionAction}</small>
                        </td>
                    `;
                    recentTbody.appendChild(row);
                });
            }

            // Populate all conflicts in conflict ledger
            const allTbody = document.getElementById('tbl-all-conflicts');
            allTbody.innerHTML = '';
            if (conflicts.length === 0) {
                allTbody.innerHTML = '<tr><td colspan="7" class="text-center text-muted">No conflicts resolved. Run simulation.</td></tr>';
            } else {
                conflicts.forEach(c => {
                    const row = document.createElement('tr');
                    const badgeClass = c.conflictType === 'DRIVER_SCARCITY' ? 'badge-conflict' : (c.conflictType === 'CAPACITY_EXCEEDED' ? 'badge-warning' : 'badge-purple');
                    const driverName = c.driver ? c.driver.name : '<span class="text-muted">None</span>';
                    row.innerHTML = `
                        <td><strong>#${c.id}</strong></td>
                        <td>Request #${c.request.id}</td>
                        <td>${driverName}</td>
                        <td><span class="badge ${badgeClass}">${c.conflictType}</span></td>
                        <td>${c.details}</td>
                        <td><code class="text-green">${c.resolutionAction}</code></td>
                        <td>${formatDate(c.resolvedAt)}</td>
                    `;
                    allTbody.appendChild(row);
                });
            }
        } catch(e) {
            console.error(e);
        }
    }

    async function loadRidesTable() {
        try {
            const response = await fetch(`${API_URL}/api/rides`);
            const rides = await response.json();
            
            const tbody = document.getElementById('tbl-all-rides');
            tbody.innerHTML = '';

            if (rides.length === 0) {
                tbody.innerHTML = '<tr><td colspan="8" class="text-center text-muted">No records found. Seed data using simulation.</td></tr>';
            } else {
                rides.slice().reverse().forEach(r => {
                    const row = document.createElement('tr');
                    const paymentBadge = r.fare > r.rider.balance && r.status === 'COMPLETED' ? 'badge-conflict' : 'badge-success';
                    const paymentText = r.fare > r.rider.balance && r.status === 'COMPLETED' ? 'FAILED (Debt)' : 'SETTLED';
                    
                    row.innerHTML = `
                        <td><strong>#${r.id}</strong></td>
                        <td>${r.rider.name}</td>
                        <td>${r.driver.name}</td>
                        <td>${r.vehicle.make} ${r.vehicle.model}</td>
                        <td>${(ridesharingServiceDistance(r.startLatitude, r.startLongitude, r.endLatitude, r.endLongitude)).toFixed(2)} km</td>
                        <td>$${r.fare.toFixed(2)}</td>
                        <td><span class="badge ${paymentBadge}">${paymentText}</span></td>
                        <td><i class="fa-solid fa-star text-gold"></i> ${r.rider.rating.toFixed(1)}</td>
                    `;
                    tbody.appendChild(row);
                });
            }
        } catch(e) {
            console.error(e);
        }
    }

    async function loadUsersTable() {
        try {
            const response = await fetch(`${API_URL}/api/users`);
            const users = await response.json();
            
            const ridersTbody = document.getElementById('tbl-all-riders');
            ridersTbody.innerHTML = '';
            
            const riders = users.filter(u => u.role === 'RIDER');
            if (riders.length === 0) {
                ridersTbody.innerHTML = '<tr><td colspan="5" class="text-center text-muted">No riders loaded.</td></tr>';
            } else {
                riders.forEach(r => {
                    const row = document.createElement('tr');
                    row.innerHTML = `
                        <td><strong>#${r.id}</strong></td>
                        <td>${r.name}</td>
                        <td>${r.email}</td>
                        <td><i class="fa-solid fa-star text-gold"></i> ${r.rating.toFixed(1)}</td>
                        <td class="${r.balance < 10 ? 'text-red font-weight-bold' : ''}">$${r.balance.toFixed(2)}</td>
                    `;
                    ridersTbody.appendChild(row);
                });
            }

            const responseVeh = await fetch(`${API_URL}/api/vehicles`);
            const vehicles = await responseVeh.json();

            const driversTbody = document.getElementById('tbl-all-drivers');
            driversTbody.innerHTML = '';
            
            const drivers = users.filter(u => u.role === 'DRIVER');
            if (drivers.length === 0) {
                driversTbody.innerHTML = '<tr><td colspan="7" class="text-center text-muted">No drivers loaded.</td></tr>';
            } else {
                drivers.forEach(d => {
                    const vehicle = vehicles.find(v => v.driver.id === d.id) || { make: 'N/A', model: '', licensePlate: 'N/A', vehicleType: 'N/A', capacity: 'N/A' };
                    const row = document.createElement('tr');
                    row.innerHTML = `
                        <td><strong>#${d.id}</strong></td>
                        <td>${d.name}</td>
                        <td>${vehicle.make} ${vehicle.model}</td>
                        <td><code>${vehicle.licensePlate}</code></td>
                        <td><span class="badge ${vehicle.vehicleType === 'LUXURY' ? 'badge-purple' : (vehicle.vehicleType === 'SUV' ? 'badge-warning' : 'badge-success')}">${vehicle.vehicleType}</span></td>
                        <td>${vehicle.capacity} seats</td>
                        <td><i class="fa-solid fa-star text-gold"></i> ${d.rating.toFixed(1)}</td>
                    `;
                    driversTbody.appendChild(row);
                });
            }

        } catch(e) {
            console.error(e);
        }
    }

    // Helper functions
    function ridesharingServiceDistance(lat1, lng1, lat2, lng2) {
        return Math.sqrt(Math.pow(lat1 - lat2, 2) + Math.pow(lng1 - lng2, 2)) * 100.0;
    }

    function formatDate(dtStr) {
        if (!dtStr) return 'N/A';
        const d = new Date(dtStr);
        return d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' });
    }

    // Bengaluru Spatial Map Visualizer on Canvas
    // Bangalore boundaries in our coordinate seeds:
    // Latitude: 12.87 to 13.07
    // Longitude: 77.49 to 77.69
    const MAP_BOUNDS = {
        minLat: 12.87,
        maxLat: 13.07,
        minLng: 77.49,
        maxLng: 77.69
    };

    function mapCoords(lat, lng, width, height) {
        const x = ((lng - MAP_BOUNDS.minLng) / (MAP_BOUNDS.maxLng - MAP_BOUNDS.minLng)) * width;
        const y = height - ((lat - MAP_BOUNDS.minLat) / (MAP_BOUNDS.maxLat - MAP_BOUNDS.minLat)) * height;
        return { x, y };
    }

    async function startMapVisualizer() {
        if (mapAnimationId) {
            cancelAnimationFrame(mapAnimationId);
        }

        try {
            const responseDrivers = await fetch(`${API_URL}/api/availability`);
            const drivers = await responseDrivers.json();

            const responseRides = await fetch(`${API_URL}/api/rides`);
            const rides = await responseRides.json();

            const responseConflicts = await fetch(`${API_URL}/api/conflicts`);
            const conflicts = await responseConflicts.json();

            // Set active feed
            populateActiveFeed(rides);

            // Prepare Animation Objects
            let animatedRides = rides.map(r => {
                const start = mapCoords(r.startLatitude, r.startLongitude, 1, 1); // normalized
                const end = mapCoords(r.endLatitude, r.endLongitude, 1, 1);
                return {
                    id: r.id,
                    riderName: r.rider.name,
                    driverName: r.driver.name,
                    startLat: r.startLatitude,
                    startLng: r.startLongitude,
                    endLat: r.endLatitude,
                    endLng: r.endLongitude,
                    progress: 0,
                    active: Math.random() > 0.5 // randomly trigger some as active moving paths
                };
            });

            isAnimationPaused = false;
            btnToggleAnim.innerHTML = '<i class="fa-solid fa-pause"></i> Pause Animation';

            function animate() {
                if (!isAnimationPaused) {
                    // Update active animated rides progress
                    animatedRides.forEach(r => {
                        if (r.active) {
                            r.progress += animationSpeed;
                            if (r.progress > 1) {
                                r.progress = 0;
                                r.active = Math.random() > 0.3; // randomly loop
                            }
                        } else if (Math.random() < 0.01) {
                            r.active = true; // randomly trigger inactive rides to start moving
                        }
                    });
                }

                // Draw on Dashboard Preview Canvas
                drawCanvas(previewCanvas, previewCtx, drivers, animatedRides, conflicts, true);
                
                // Draw on Fullscreen Canvas
                drawCanvas(fullCanvas, fullCtx, drivers, animatedRides, conflicts, false);

                mapAnimationId = requestAnimationFrame(animate);
            }

            animate();

        } catch (e) {
            console.error('Map animation startup failed', e);
        }
    }

    function populateActiveFeed(rides) {
        const feed = document.getElementById('active-rides-feed');
        feed.innerHTML = '';
        const activeRides = rides.filter(r => r.status === 'COMPLETED').slice(-6).reverse();

        if (activeRides.length === 0) {
            feed.innerHTML = '<div class="feed-placeholder">No active rides. Run simulation to seed map events.</div>';
            return;
        }

        activeRides.forEach(r => {
            const item = document.createElement('div');
            item.className = 'feed-item';
            item.innerHTML = `
                <div class="feed-item-header">
                    <span class="text-cyan"><i class="fa-solid fa-car-side"></i> Ride #${r.id}</span>
                    <span class="badge badge-success">COMPLETED</span>
                </div>
                <div>Rider: <strong>${r.rider.name}</strong></div>
                <div>Driver: <strong>${r.driver.name}</strong></div>
                <div>Settled Fare: <span class="text-green">$${r.fare.toFixed(2)}</span></div>
            `;
            feed.appendChild(item);
        });
    }

    function drawCanvas(canvas, ctx, drivers, animatedRides, conflicts, isPreview) {
        const w = canvas.width;
        const h = canvas.height;

        ctx.clearRect(0, 0, w, h);

        // 1. Draw Bengaluru Grid lines representing roads
        ctx.strokeStyle = 'rgba(255, 255, 255, 0.03)';
        ctx.lineWidth = 1;
        const gridSpacing = isPreview ? 25 : 40;
        for (let i = 0; i < w; i += gridSpacing) {
            ctx.beginPath();
            ctx.moveTo(i, 0);
            ctx.lineTo(i, h);
            ctx.stroke();
        }
        for (let i = 0; i < h; i += gridSpacing) {
            ctx.beginPath();
            ctx.moveTo(0, i);
            ctx.lineTo(w, i);
            ctx.stroke();
        }

        // Draw radial tech grid ring around central Bangalore
        const center = mapCoords(12.9716, 77.5946, w, h);
        ctx.strokeStyle = 'rgba(138, 63, 252, 0.05)';
        ctx.lineWidth = 2;
        ctx.beginPath();
        ctx.arc(center.x, center.y, isPreview ? 60 : 120, 0, Math.PI * 2);
        ctx.stroke();
        ctx.beginPath();
        ctx.arc(center.x, center.y, isPreview ? 120 : 220, 0, Math.PI * 2);
        ctx.stroke();

        // 2. Draw conflicts (deliberate failure points)
        conflicts.forEach(c => {
            const lat = c.request.startLatitude;
            const lng = c.request.startLongitude;
            const pt = mapCoords(lat, lng, w, h);
            
            ctx.shadowBlur = 10;
            ctx.shadowColor = '#ff4b2b';
            
            // Draw red glowing cross/conflict point
            ctx.strokeStyle = 'rgba(255, 75, 43, 0.8)';
            ctx.lineWidth = 2;
            ctx.beginPath();
            ctx.moveTo(pt.x - 6, pt.y - 6);
            ctx.lineTo(pt.x + 6, pt.y + 6);
            ctx.moveTo(pt.x + 6, pt.y - 6);
            ctx.lineTo(pt.x - 6, pt.y + 6);
            ctx.stroke();
            
            // Label conflict ID if fullscreen
            if (!isPreview) {
                ctx.fillStyle = 'rgba(255, 75, 43, 0.9)';
                ctx.font = '9px sans-serif';
                ctx.fillText(`Conflict: ${c.conflictType}`, pt.x + 8, pt.y + 3);
            }
        });

        // 3. Draw matched moving rides (glowing trails)
        animatedRides.forEach(r => {
            if (!r.active) return;
            const startPt = mapCoords(r.startLat, r.startLng, w, h);
            const endPt = mapCoords(r.endLat, r.endLng, w, h);

            // Draw connecting path
            ctx.shadowBlur = 0;
            ctx.strokeStyle = 'rgba(138, 63, 252, 0.15)';
            ctx.lineWidth = 1.5;
            ctx.beginPath();
            ctx.moveTo(startPt.x, startPt.y);
            ctx.lineTo(endPt.x, endPt.y);
            ctx.stroke();

            // Draw moving vehicle dot
            const cx = startPt.x + (endPt.x - startPt.x) * r.progress;
            const cy = startPt.y + (endPt.y - startPt.y) * r.progress;
            
            ctx.shadowBlur = 12;
            ctx.shadowColor = '#00ff87';
            ctx.fillStyle = '#00ff87';
            ctx.beginPath();
            ctx.arc(cx, cy, isPreview ? 4 : 6, 0, Math.PI * 2);
            ctx.fill();
        });

        // 4. Draw Available Drivers
        drivers.forEach(d => {
            if (!d.isAvailable) return;
            const pt = mapCoords(d.currentLatitude, d.currentLongitude, w, h);
            
            ctx.shadowBlur = 10;
            ctx.shadowColor = '#00f2fe';
            ctx.fillStyle = '#00f2fe';
            
            ctx.beginPath();
            ctx.arc(pt.x, pt.y, isPreview ? 3 : 5, 0, Math.PI * 2);
            ctx.fill();

            if (!isPreview) {
                ctx.fillStyle = 'rgba(0, 242, 254, 0.8)';
                ctx.font = '8px sans-serif';
                ctx.fillText(d.driver.name, pt.x + 8, pt.y + 3);
            }
        });

        // Reset shadow
        ctx.shadowBlur = 0;
    }

    // Toggle animation action
    btnToggleAnim.addEventListener('click', () => {
        isAnimationPaused = !isAnimationPaused;
        if (isAnimationPaused) {
            btnToggleAnim.innerHTML = '<i class="fa-solid fa-play"></i> Resume Animation';
        } else {
            btnToggleAnim.innerHTML = '<i class="fa-solid fa-pause"></i> Pause Animation';
        }
    });

    // Initial load
    loadDashboardStats();
});
