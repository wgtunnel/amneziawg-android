package junktag

import (
	"fmt"
	"testing"

	"github.com/stretchr/testify/require"
)

func Test_newBytesGenerator(t *testing.T) {
	type args struct {
		param string
	}
	tests := []struct {
		name    string
		args    args
		want    []byte
		wantErr error
	}{
		{
			name: "empty",
			args: args{
				param: "",
			},
			wantErr: fmt.Errorf("not correct hex"),
		},
		{
			name: "wrong start",
			args: args{
				param: "123456",
			},
			wantErr: fmt.Errorf("not correct hex"),
		},
		{
			name: "not only hex value",
			args: args{
				param: "0x12345q",
			},
			wantErr: fmt.Errorf("not correct hex"),
		},
		{
			name: "valid hex",
			args: args{
				param: "0xf6ab3267fa",
			},
			want: []byte{0xf6, 0xab, 0x32, 0x67, 0xfa},
		},
		{
			name: "valid hex with odd length",
			args: args{
				param: "0xfab3267fa",
			},
			want: []byte{0xf, 0xab, 0x32, 0x67, 0xfa},
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			got, err := newBytesGenerator(tt.args.param)

			if tt.wantErr != nil {
				require.ErrorAs(t, err, &tt.wantErr)
				require.Nil(t, got)
				return
			}

			require.Nil(t, err)
			require.NotNil(t, got)

			gotValues, _ := got.Generate()
			require.Equal(t, tt.want, gotValues)
		})
	}
}

func Test_newRandomPacketGenerator(t *testing.T) {
	type args struct {
		param string
	}
	tests := []struct {
		name    string
		args    args
		wantErr error
	}{
		{
			name: "empty",
			args: args{
				param: "",
			},
			wantErr: fmt.Errorf("parse int"),
		},
		{
			name: "not an int",
			args: args{
				param: "x",
			},
			wantErr: fmt.Errorf("parse int"),
		},
		{
			name: "valid",
			args: args{
				param: "12",
			},
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			got, err := newRandomPacketGenerator(tt.args.param)
			if tt.wantErr != nil {
				require.ErrorAs(t, err, &tt.wantErr)
				require.Nil(t, got)
				return
			}

			require.Nil(t, err)
			require.NotNil(t, got)
			first, err := got.Generate()
			require.Nil(t, err)

			second, err := got.Generate()
			require.Nil(t, err)
			require.NotEqual(t, first, second)
		})
	}
}
