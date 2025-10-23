#!/bin/bash

# Batch Planet Generator - Generate multiple planet variants efficiently
# Usage: ./batch_generate.sh [config_file] [output_dir]
# Default: ./batch_generate.sh batch_config.ini output_batch

set -e  # Exit on error

# Configuration
CONFIG_FILE="${1:-batch_config.ini}"
OUTPUT_BASE="${2:-output_batch}"
GRADLE_CMD="gradle -q run --args"
START_TIME=$(date +%s)
TOTAL_PLANETS=0
TOTAL_TIME=0

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}╔════════════════════════════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║          Batch Planet Generator v1.0                           ║${NC}"
echo -e "${BLUE}╚════════════════════════════════════════════════════════════════╝${NC}"
echo

# Check if config file exists
if [ ! -f "$CONFIG_FILE" ]; then
    echo -e "${RED}Error: Config file not found: $CONFIG_FILE${NC}"
    echo "Creating example config file..."

    cat > "$CONFIG_FILE" << 'EOF'
# Batch Generation Configuration
# Lines starting with # are ignored
# Each [section] defines one planet generation

[planet_1]
seed=42
resolution=512x256
preset=earthlike
export=albedo,height,normal,roughness,clouds,ao

[planet_2]
seed=123
resolution=512x256
preset=desert
export=albedo,height,clouds,ao

[planet_3]
seed=999
resolution=512x256
preset=ice
export=albedo,height,clouds

[planet_4]
seed=777
resolution=512x256
preset=lava
export=albedo,height,emissive,ao

[planet_5]
seed=555
resolution=256x128
preset=alien
export=albedo,height,clouds,ao
EOF

    echo -e "${GREEN}Created example config: $CONFIG_FILE${NC}"
    echo -e "${YELLOW}Edit the config file and run again${NC}"
    exit 0
fi

echo "Configuration: $CONFIG_FILE"
echo "Output directory: $OUTPUT_BASE"
echo "Gradle command: $GRADLE_CMD"
echo

# Create output directory
mkdir -p "$OUTPUT_BASE"

# Parse configuration and extract sections
sections=$(grep -E '^\[' "$CONFIG_FILE" | sed 's/\[//g' | sed 's/\]//g')

if [ -z "$sections" ]; then
    echo -e "${RED}Error: No planet configurations found in $CONFIG_FILE${NC}"
    exit 1
fi

# Counter for status
planet_num=0
success_count=0
failed_count=0

# Process each planet
for section in $sections; do
    planet_num=$((planet_num + 1))
    echo -e "${YELLOW}────────────────────────────────────────────────────────────────${NC}"
    echo -e "${BLUE}Planet $planet_num: $section${NC}"
    echo -e "${YELLOW}────────────────────────────────────────────────────────────────${NC}"

    # Extract parameters for this section
    in_section=0
    seed=""
    resolution=""
    preset=""
    export_opts=""

    while IFS='=' read -r key value; do
        # Remove whitespace
        key=$(echo "$key" | xargs)
        value=$(echo "$value" | xargs)

        # Skip comments and empty lines
        [[ "$key" =~ ^#.*$ ]] && continue
        [[ -z "$key" ]] && continue

        # Check for section header
        if [[ "$key" =~ ^\[.*\]$ ]]; then
            current_section=$(echo "$key" | sed 's/\[//g' | sed 's/\]//g')
            if [ "$current_section" = "$section" ]; then
                in_section=1
            else
                in_section=0
            fi
            continue
        fi

        # Only process parameters in the current section
        if [ $in_section -eq 1 ]; then
            case "$key" in
                seed) seed="$value" ;;
                resolution) resolution="$value" ;;
                preset) preset="$value" ;;
                export) export_opts="$value" ;;
            esac
        fi
    done < "$CONFIG_FILE"

    # Validate parameters
    if [ -z "$seed" ] || [ -z "$resolution" ] || [ -z "$preset" ]; then
        echo -e "${RED}Error: Missing required parameters (seed, resolution, preset)${NC}"
        echo "  Found: seed=$seed, resolution=$resolution, preset=$preset"
        failed_count=$((failed_count + 1))
        continue
    fi

    # Use provided export options or default
    if [ -z "$export_opts" ]; then
        export_opts="albedo,height,normal,roughness,clouds"
    fi

    # Create planet-specific output directory
    planet_output="$OUTPUT_BASE/${section}_${resolution}"
    mkdir -p "$planet_output"

    # Build command
    cmd="--seed $seed --resolution $resolution --preset $preset --export $export_opts --out $planet_output"

    echo "Parameters:"
    echo "  Seed: $seed"
    echo "  Resolution: $resolution"
    echo "  Preset: $preset"
    echo "  Export: $export_opts"
    echo "  Output: $planet_output"
    echo
    echo "Generating..."

    # Generate planet with timing
    planet_start=$(date +%s%N)

    if $GRADLE_CMD "$cmd" > /dev/null 2>&1; then
        planet_end=$(date +%s%N)
        elapsed_ms=$(( (planet_end - planet_start) / 1000000 ))
        elapsed_sec=$(echo "scale=1; $elapsed_ms / 1000" | bc)

        # Count output files
        file_count=$(find "$planet_output" -name "*.png" 2>/dev/null | wc -l)
        total_size=$(du -sh "$planet_output" 2>/dev/null | cut -f1)

        echo -e "${GREEN}✓ Success!${NC}"
        echo "  Time: ${elapsed_sec}s"
        echo "  Files: $file_count"
        echo "  Size: $total_size"

        success_count=$((success_count + 1))
        TOTAL_TIME=$((TOTAL_TIME + elapsed_ms))
    else
        echo -e "${RED}✗ Failed!${NC}"
        echo "  Check $planet_output for details"
        failed_count=$((failed_count + 1))
    fi

    echo
done

# Summary
END_TIME=$(date +%s)
TOTAL_ELAPSED=$((END_TIME - START_TIME))
AVG_TIME=0
if [ $success_count -gt 0 ]; then
    AVG_TIME=$(echo "scale=1; $TOTAL_TIME / $success_count / 1000" | bc)
fi

echo -e "${BLUE}╔════════════════════════════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║                     BATCH SUMMARY                              ║${NC}"
echo -e "${BLUE}╚════════════════════════════════════════════════════════════════╝${NC}"
echo
echo "Total planets: $planet_num"
echo -e "Successful: ${GREEN}$success_count${NC}"
if [ $failed_count -gt 0 ]; then
    echo -e "Failed: ${RED}$failed_count${NC}"
fi
echo "Average time per planet: ${AVG_TIME}s"
echo "Total batch time: ${TOTAL_ELAPSED}s"
echo
echo "Output directory: $OUTPUT_BASE"
echo "Total size: $(du -sh "$OUTPUT_BASE" | cut -f1)"
echo

# Final status
if [ $failed_count -eq 0 ]; then
    echo -e "${GREEN}All planets generated successfully!${NC}"
    exit 0
else
    echo -e "${RED}Some planets failed to generate${NC}"
    exit 1
fi
