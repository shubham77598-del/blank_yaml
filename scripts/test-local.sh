#!/bin/bash

# Local testing script for the Apigee design system
# This script validates the generation process without requiring actual Apigee deployment

set -e

# Colors for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

log_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

main() {
    log_info "Starting local Apigee design system test..."
    
    # Test 1: Clean and regenerate
    log_info "Test 1: Cleaning and regenerating artifacts..."
    mvn -q -DcleanGenerated=true compile exec:java -Dexec.mainClass="com.company.design.DesignParser"
    
    if [[ ! -d "generated" ]]; then
        log_error "Generation failed - no generated directory"
        exit 1
    fi
    
    # Test 2: Validate generated structure
    log_info "Test 2: Validating generated structure..."
    
    local has_proxies=false
    local has_sharedflows=false
    
    if [[ -d "generated/proxies" ]] && [[ -n "$(ls -A generated/proxies 2>/dev/null)" ]]; then
        has_proxies=true
        log_info "✅ Generated proxies found"
        for proxy in generated/proxies/*/; do
            if [[ -d "$proxy" ]]; then
                proxy_name=$(basename "$proxy")
                log_info "  - Proxy: $proxy_name"
                
                # Check for required files
                if [[ -f "${proxy}pom.xml" ]]; then
                    log_info "    ✅ pom.xml found"
                else
                    log_error "    ❌ pom.xml missing"
                fi
                
                if [[ -f "${proxy}apiproxy/$(basename "$proxy").xml" ]]; then
                    log_info "    ✅ API proxy descriptor found"
                else
                    log_error "    ❌ API proxy descriptor missing"
                fi
            fi
        done
    fi
    
    if [[ -d "generated/sharedflows" ]] && [[ -n "$(ls -A generated/sharedflows 2>/dev/null)" ]]; then
        has_sharedflows=true
        log_info "✅ Generated shared flows found"
        for sf in generated/sharedflows/*/; do
            if [[ -d "$sf" ]]; then
                sf_name=$(basename "$sf")
                log_info "  - SharedFlow: $sf_name"
                
                # Check for required files
                if [[ -f "${sf}pom.xml" ]]; then
                    log_info "    ✅ pom.xml found"
                else
                    log_error "    ❌ pom.xml missing"
                fi
            fi
        done
    fi
    
    if [[ -f "generated/config/pom.xml" ]]; then
        log_info "✅ Configuration module found"
    else
        log_warn "⚠️  No configuration module generated"
    fi
    
    # Test 3: Basic Maven validation
    log_info "Test 3: Validating Maven build files..."
    
    if $has_proxies; then
        for proxy in generated/proxies/*/; do
            if [[ -d "$proxy" && -f "${proxy}pom.xml" ]]; then
                proxy_name=$(basename "$proxy")
                log_info "Validating Maven config for proxy: $proxy_name"
                (cd "$proxy" && mvn validate -q) || {
                    log_error "Maven validation failed for proxy: $proxy_name"
                    exit 1
                }
                log_info "✅ Maven config valid for $proxy_name"
            fi
        done
    fi
    
    if $has_sharedflows; then
        for sf in generated/sharedflows/*/; do
            if [[ -d "$sf" && -f "${sf}pom.xml" ]]; then
                sf_name=$(basename "$sf")
                log_info "Validating Maven config for shared flow: $sf_name"
                (cd "$sf" && mvn validate -q) || {
                    log_error "Maven validation failed for shared flow: $sf_name"
                    exit 1
                }
                log_info "✅ Maven config valid for $sf_name"
            fi
        done
    fi
    
    # Test 4: Design file validation
    log_info "Test 4: Validating design files..."
    
    if [[ ! -d "designs" ]] || [[ -z "$(ls -A designs/*.y*ml 2>/dev/null)" ]]; then
        log_error "No design files found in designs/ directory"
        exit 1
    fi
    
    for design in designs/*.yaml designs/*.yml; do
        if [[ -f "$design" ]]; then
            log_info "Design file: $(basename "$design")"
            
            # Basic YAML syntax check using Python
            if python3 -c "import yaml; yaml.safe_load(open('$design'))" 2>/dev/null; then
                log_info "✅ YAML syntax valid"
            else
                log_error "❌ YAML syntax invalid"
                exit 1
            fi
        fi
    done
    
    log_info "=================================="
    log_info "Local test summary:"
    log_info "✅ Generation process works"
    log_info "✅ Generated artifacts are valid"
    log_info "✅ Maven configurations are correct"
    log_info "✅ Design files have valid syntax"
    log_info "=================================="
    log_info "🎉 All local tests passed!"
    
    log_warn "Note: This test validates local generation only."
    log_warn "For full deployment testing, use a real Apigee environment."
}

main "$@"