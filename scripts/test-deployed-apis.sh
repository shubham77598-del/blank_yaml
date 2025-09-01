#!/bin/bash

# API Deployment Verification Script
# This script tests if deployed Apigee APIs are actually working and responding to requests

set -e

# Configuration from environment variables
APIGEE_ORG="${APIGEE_ORG:-}"
APIGEE_ENV="${APIGEE_ENV:-}"
APIGEE_HOST="${APIGEE_HOST:-${APIGEE_ORG}-${APIGEE_ENV}.apigee.net}"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Logging functions
log_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Function to test API endpoint
test_api_endpoint() {
    local proxy_name="$1"
    local base_path="$2"
    local expected_status="${3:-200}"
    local timeout="${4:-30}"
    
    log_info "Testing API: ${proxy_name} at ${base_path}"
    
    local url="https://${APIGEE_HOST}${base_path}"
    log_info "Making request to: ${url}"
    
    # Try to make a request to the API
    local response
    local http_status
    
    # Using curl with timeout and following redirects
    response=$(curl -s -w "\n%{http_code}" --max-time "${timeout}" \
                   -H "Accept: application/json" \
                   -H "User-Agent: Apigee-Test-Script/1.0" \
                   "${url}" || echo -e "\nCURL_ERROR")
    
    # Extract HTTP status code from response
    http_status=$(echo "$response" | tail -n1)
    
    # Check if curl failed
    if [[ "$http_status" == "CURL_ERROR" ]]; then
        log_error "Failed to connect to API endpoint: ${url}"
        return 1
    fi
    
    # Check if we got a valid HTTP status code
    if [[ ! "$http_status" =~ ^[0-9]{3}$ ]]; then
        log_error "Invalid HTTP status code received: ${http_status}"
        return 1
    fi
    
    log_info "HTTP Status: ${http_status}"
    
    # Check if status code indicates success (2xx or 3xx)
    if [[ "$http_status" -ge 200 && "$http_status" -lt 400 ]]; then
        log_info "✅ API endpoint is responding successfully"
        return 0
    elif [[ "$http_status" -eq 401 || "$http_status" -eq 403 ]]; then
        log_warn "⚠️  API returned authentication error (${http_status}) - this is expected for secured endpoints"
        return 0
    else
        log_error "❌ API returned error status: ${http_status}"
        return 1
    fi
}

# Function to test API with retry logic
test_api_with_retry() {
    local proxy_name="$1"
    local base_path="$2"
    local max_retries="${3:-3}"
    local retry_delay="${4:-10}"
    
    log_info "Testing ${proxy_name} with up to ${max_retries} retries..."
    
    for ((i=1; i<=max_retries; i++)); do
        log_info "Attempt ${i}/${max_retries}"
        
        if test_api_endpoint "$proxy_name" "$base_path"; then
            log_info "✅ ${proxy_name} is working correctly"
            return 0
        fi
        
        if [[ $i -lt $max_retries ]]; then
            log_warn "Retrying in ${retry_delay} seconds..."
            sleep "$retry_delay"
        fi
    done
    
    log_error "❌ ${proxy_name} failed all ${max_retries} attempts"
    return 1
}

# Function to discover and test APIs from generated configuration
discover_and_test_apis() {
    log_info "Discovering deployed APIs from generated configurations..."
    
    local test_failures=0
    local total_tests=0
    
    # Look for generated proxy configurations
    if [[ -d "generated/proxies" ]]; then
        for proxy_dir in generated/proxies/*/; do
            if [[ -d "$proxy_dir" ]]; then
                proxy_name=$(basename "$proxy_dir")
                log_info "Found proxy: ${proxy_name}"
                
                # Try to extract base path from proxy configuration
                local base_path=""
                local proxy_file="${proxy_dir}apiproxy/proxies/default.xml"
                
                if [[ -f "$proxy_file" ]]; then
                    base_path=$(grep -o '<BasePath>[^<]*</BasePath>' "$proxy_file" 2>/dev/null | sed 's/<BasePath>//g; s/<\/BasePath>//g' | head -1)
                fi
                
                if [[ -n "$base_path" ]]; then
                    log_info "Extracted base path: ${base_path}"
                    total_tests=$((total_tests + 1))
                    
                    if ! test_api_with_retry "$proxy_name" "$base_path"; then
                        test_failures=$((test_failures + 1))
                    fi
                else
                    log_warn "Could not determine base path for proxy: ${proxy_name}"
                fi
            fi
        done
    fi
    
    # Summary
    log_info "=================================="
    log_info "API Testing Summary:"
    log_info "Total tests: ${total_tests}"
    log_info "Failures: ${test_failures}"
    log_info "Success rate: $(( (total_tests - test_failures) * 100 / (total_tests == 0 ? 1 : total_tests) ))%"
    log_info "=================================="
    
    if [[ $test_failures -gt 0 ]]; then
        log_error "Some APIs failed verification!"
        return 1
    else
        log_info "All APIs are responding correctly! 🎉"
        return 0
    fi
}

# Main execution
main() {
    log_info "Starting API deployment verification..."
    log_info "Apigee Org: ${APIGEE_ORG}"
    log_info "Apigee Env: ${APIGEE_ENV}"
    log_info "Apigee Host: ${APIGEE_HOST}"
    
    # Validate required environment variables
    if [[ -z "$APIGEE_ORG" ]]; then
        log_error "APIGEE_ORG environment variable is required"
        exit 1
    fi
    
    if [[ -z "$APIGEE_ENV" ]]; then
        log_error "APIGEE_ENV environment variable is required"
        exit 1
    fi
    
    # Wait a bit for deployments to stabilize
    log_info "Waiting 30 seconds for deployments to stabilize..."
    sleep 30
    
    # Run the tests
    if discover_and_test_apis; then
        log_info "✅ All API deployment verifications passed!"
        exit 0
    else
        log_error "❌ API deployment verification failed!"
        exit 1
    fi
}

# Run main function
main "$@"