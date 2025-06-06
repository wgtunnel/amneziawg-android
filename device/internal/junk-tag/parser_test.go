package junktag

import (
	"fmt"
	"testing"

	"github.com/stretchr/testify/require"
)

func TestParse(t *testing.T) {
	type args struct {
		input string
	}
	tests := []struct {
		name    string
		args    args
		wantErr error
	}{
		{
			name:    "empty",
			args:    args{input: ""},
			wantErr: fmt.Errorf("ill formated input"),
		},
		{
			name:    "extra >",
			args:    args{input: "<b 0xf6ab3267fa><c>>"},
			wantErr: fmt.Errorf("ill formated input"),
		},
		{
			name:    "extra <",
			args:    args{input: "<<b 0xf6ab3267fa><c>"},
			wantErr: fmt.Errorf("empty tag in input"),
		},
		{
			name:    "empty <>",
			args:    args{input: "<><b 0xf6ab3267fa><c>"},
			wantErr: fmt.Errorf("empty tag in input"),
		},
		{
			name:    "invalid tag",
			args:    args{input: "<q 0xf6ab3267fa>"},
			wantErr: fmt.Errorf("invalid tag"),
		},
		{
			name: "valid",
			args: args{input: "<b 0xf6ab3267fa><c><b 0xf6ab><t><r 10><wt 10>"},
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			_, err := Parse(tt.args.input)

			// TODO:  ErrorAs doesn't work as you think
			if tt.wantErr != nil {
				require.ErrorAs(t, err, &tt.wantErr)
				return
			}
			require.Nil(t, err)
		})
	}
}
