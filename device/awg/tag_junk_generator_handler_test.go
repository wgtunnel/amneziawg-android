package awg

import (
	"testing"

	"github.com/amnezia-vpn/amneziawg-go/device/awg/internal"
	"github.com/stretchr/testify/require"
)

func TestTagJunkGeneratorHandlerAppendGenerator(t *testing.T) {
	t.Parallel()

	tests := []struct {
		name      string
		generator TagJunkGenerator
	}{
		{
			name:      "append single generator",
			generator: newTagJunkGenerator("t1", 10),
		},
	}

	for _, tt := range tests {
		tt := tt
		t.Run(tt.name, func(t *testing.T) {
			t.Parallel()
			handler := &TagJunkGeneratorHandler{}

			// Initial length should be 0
			require.Equal(t, 0, handler.length)
			require.Empty(t, handler.generators)

			// After append, length should be 1 and generator should be added
			handler.AppendGenerator(tt.generator)
			require.Equal(t, 1, handler.length)
			require.Len(t, handler.generators, 1)
			require.Equal(t, tt.generator, handler.generators[0])
		})
	}
}

func TestTagJunkGeneratorHandlerValidate(t *testing.T) {
	t.Parallel()

	tests := []struct {
		name       string
		generators []TagJunkGenerator
		wantErr    bool
		errMsg     string
	}{
		{
			name: "valid consecutive indices",
			generators: []TagJunkGenerator{
				newTagJunkGenerator("t1", 10),
				newTagJunkGenerator("t2", 10),
			},
			wantErr: false,
		},
		{
			name: "non-consecutive indices",
			generators: []TagJunkGenerator{
				newTagJunkGenerator("t1", 10),
				newTagJunkGenerator("t3", 10), // Missing t2
			},
			wantErr: true,
			errMsg:  "junk packet index should be consecutive",
		},
		{
			name: "nameIndex error",
			generators: []TagJunkGenerator{
				newTagJunkGenerator("error", 10),
			},
			wantErr: true,
			errMsg:  "name must be 2 character long",
		},
	}

	for _, tt := range tests {
		tt := tt
		t.Run(tt.name, func(t *testing.T) {
			t.Parallel()
			handler := &TagJunkGeneratorHandler{}
			for _, gen := range tt.generators {
				handler.AppendGenerator(gen)
			}

			err := handler.Validate()
			if tt.wantErr {
				require.Error(t, err)
				require.Contains(t, err.Error(), tt.errMsg)
			} else {
				require.NoError(t, err)
			}
		})
	}
}

func TestTagJunkGeneratorHandlerGenerate(t *testing.T) {
	t.Parallel()

	mockByte1 := []byte{0x01, 0x02}
	mockByte2 := []byte{0x03, 0x04, 0x05}
	mockGen1 := internal.NewMockByteGenerator(mockByte1)
	mockGen2 := internal.NewMockByteGenerator(mockByte2)

	tests := []struct {
		name           string
		setupGenerator func() []TagJunkGenerator
		expected       [][]byte
	}{
		{
			name: "generate with no default junk",
			setupGenerator: func() []TagJunkGenerator {
				tg1 := newTagJunkGenerator("t1", 0)
				tg1.append(mockGen1)
				tg1.append(mockGen2)
				tg2 := newTagJunkGenerator("t2", 0)
				tg2.append(mockGen2)
				tg2.append(mockGen1)

				return []TagJunkGenerator{tg1, tg2}
			},
			expected: [][]byte{
				append(mockByte1, mockByte2...),
				append(mockByte2, mockByte1...),
			},
		},
	}

	for _, tt := range tests {
		tt := tt
		t.Run(tt.name, func(t *testing.T) {
			t.Parallel()
			handler := &TagJunkGeneratorHandler{}
			generators := tt.setupGenerator()
			for _, gen := range generators {
				handler.AppendGenerator(gen)
			}

			result := handler.GeneratePackets()
			require.Equal(t, result, tt.expected)
		})
	}
}
